package com.savie.precut.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.savie.precut.ai.cloud.await
import com.savie.precut.auth.AccessTokens
import com.savie.precut.auth.AuthConfig
import com.savie.precut.data.AppSettings
import com.savie.precut.data.ProjectRepository
import com.savie.precut.data.db.MediaFileDao
import com.savie.precut.data.db.MediaFileEntity
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.Project
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import com.savie.precut.model.look.FilterCatalog
import com.savie.precut.model.sync.CloudRef
import com.savie.precut.model.sync.cloudRefs
import com.savie.precut.model.sync.customLutIds
import com.savie.precut.model.sync.extensionOf
import com.savie.precut.model.sync.fromCloud
import com.savie.precut.model.sync.importedFontIds
import com.savie.precut.model.sync.localMediaUris
import com.savie.precut.model.sync.toCloud
import com.savie.precut.model.text.BundledFont
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A project's files and the account's (Phase 30D): each file is hashed once (while it doesn't
 * change), uploaded once per account (resumable), and downloaded to `files/cloud` on other phones;
 * imported fonts and custom LUTs go back to their own folders. Videos and photos move on Wi-Fi
 * only unless Settings → Account allows mobile data.
 */
@Singleton
class CloudMedia @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: MediaFileDao,
    private val api: ProjectSyncApi,
    private val tokens: AccessTokens,
    private val tus: TusUpload,
    private val auth: AuthConfig,
    private val settings: AppSettings,
    private val projects: ProjectRepository,
    private val http: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ProjectMedia {

    private sealed interface Upload {
        data class InCloud(val ref: CloudRef) : Upload
        /** Unreadable (deleted from the gallery) or too large: it stays out of the cloud. */
        data object Skip : Upload
        data class Stop(val prepared: Prepared) : Upload
    }

    override suspend fun prepare(project: Project): Prepared {
        val refs = mutableMapOf<String, CloudRef>()
        val extra = mutableMapOf<String, String>()
        suspend fun put(uri: String, onRef: (CloudRef) -> Unit): Prepared? = when (val result = upload(uri)) {
            is Upload.InCloud -> null.also { onRef(result.ref) }
            Upload.Skip -> null
            is Upload.Stop -> result.prepared
        }
        for (uri in project.localMediaUris()) put(uri) { refs[uri] = it }?.let { return it }
        for (id in project.importedFontIds()) put(fileUri(fontFile(id))) { extra[id] = it.tail() }?.let { return it }
        for (id in project.customLutIds()) put(fileUri(lutFile(id))) { extra[id] = it.tail() }?.let { return it }
        val hashes = (refs.values.map { it.sha256 } + extra.values.map { it.substringBefore('.') }).distinct()
        return Prepared.Ready(project.toCloud(refs), extra, hashes)
    }

    private suspend fun upload(uri: String): Upload {
        val row = known(uri) ?: return Upload.Skip
        val ref = CloudRef(row.sha256, extensionOf(uri) ?: extensionFor(row.mime))
        if (row.inCloud) return Upload.InCloud(ref)
        if (!mediaAllowed()) return Upload.Stop(Prepared.Wait(SyncNotice.WaitingForWifi))
        val grant = when (val reply = tokens.withToken { api.grant(it, row.sha256, row.size, row.mime) }) {
            is AuthResult.Ok -> reply.value
            is AuthResult.Failed -> return Upload.Stop(Prepared.Failed(reply.failure))
        }
        when (grant) {
            GrantReply.Exists -> Unit
            GrantReply.TooLarge -> return Upload.Skip
            is GrantReply.StorageFull -> return Upload.Stop(Prepared.Wait(SyncNotice.StorageFull(grant.used, grant.limit)))
            is GrantReply.Upload -> {
                val file = TusFile(grant.endpoint, grant.bucket, grant.path, row.mime, row.size) { open(uri) }
                val sent = tus.upload(
                    file, auth.publishableKey, row.uploadUrl,
                    token = { tokens.withToken { AuthResult.Ok(it) } },
                    onCreated = { files.setUploadUrl(uri, it) },
                )
                if (sent is TusResult.Failed) return Upload.Stop(Prepared.Failed(sent.failure))
                val confirmed = when (val reply = tokens.withToken { api.confirm(it, row.sha256) }) {
                    is AuthResult.Ok -> reply.value
                    is AuthResult.Failed -> return Upload.Stop(Prepared.Failed(reply.failure))
                }
                if (confirmed != ConfirmReply.Ok) {
                    files.setUploadUrl(uri, null)
                    return Upload.Stop(Prepared.Failed(AuthFailure.Busy))
                }
            }
        }
        files.markInCloud(row.sha256)
        return Upload.InCloud(ref)
    }

    /**
     * The file's row, hashed again when its size or date changed; null when it can't be read. A
     * file still to download here is known by its row: it's already in the cloud.
     */
    private suspend fun known(uri: String): MediaFileEntity? = withContext(io) {
        val row = files.get(uri)
        val stat = stat(uri) ?: return@withContext row?.takeIf { it.inCloud && it.sha256.isNotEmpty() }
        if (row != null && row.size == stat.size && (stat.modified == 0L || row.modified == stat.modified) && row.sha256.isNotEmpty()) {
            return@withContext row
        }
        val sha = hash(uri) ?: return@withContext null
        val inCloud = files.withHash(sha).any { it.inCloud }
        MediaFileEntity(uri, sha, stat.size, stat.modified, stat.mime, inCloud).also { files.upsert(it) }
    }

    override suspend fun localize(project: Project, extra: Map<String, String>): Project {
        for ((id, tail) in extra) {
            val ref = CloudRef.parse("cloud:$tail") ?: continue
            val target = when {
                id.startsWith(BundledFont.IMPORTED_PREFIX) -> fontFile(id)
                id.startsWith(FilterCatalog.CUSTOM_PREFIX) -> lutFile(id)
                else -> continue
            }
            expect(fileUri(target), ref)
        }
        val local = project.cloudRefs().associateWith { placeFor(it) }
        return project.fromCloud { local[it] ?: fileUri(cloudFile(it)) }
    }

    /** A file this phone already has with that content, or where the download goes. */
    private suspend fun placeFor(ref: CloudRef): String = withContext(io) {
        files.withHash(ref.sha256).firstOrNull { stat(it.uri) != null }?.uri
            ?: fileUri(cloudFile(ref)).also { expect(it, ref) }
    }

    /** Notes that [uri] should hold the account's file [ref] (downloaded when missing). */
    private suspend fun expect(uri: String, ref: CloudRef) {
        val row = files.get(uri)
        if (row == null || row.sha256 != ref.sha256) {
            files.upsert(MediaFileEntity(uri, ref.sha256, size = 0, modified = 0, mime = "", inCloud = true))
        }
    }

    override suspend fun forget(hashes: List<String>) {
        hashes.forEach { sha -> files.withHash(sha).forEach { files.upsert(it.copy(inCloud = false, uploadUrl = null)) } }
    }

    override suspend fun downloadMissing(): List<String> {
        val needed = projects.loadAll().flatMap { p ->
            p.localMediaUris() + p.importedFontIds().map { fileUri(fontFile(it)) } + p.customLutIds().map { fileUri(lutFile(it)) }
        }.toSet()
        val missing = files.appFiles().filter { it.inCloud && it.uri in needed && !File(it.uri.toUri().path.orEmpty()).exists() }
        if (missing.isEmpty() || !mediaAllowed()) return emptyList()
        val arrived = mutableListOf<String>()
        for (row in missing) {
            val url = (tokens.withToken { api.downloadUrl(it, row.sha256) } as? AuthResult.Ok)?.value ?: continue
            if (download(url, File(row.uri.toUri().path.orEmpty()), row.sha256)) arrived += row.uri
        }
        return arrived
    }

    /** Streams [url] into [target], checking its SHA-256 before it takes the name. */
    private suspend fun download(url: String, target: File, sha256: String): Boolean = withContext(io) {
        val temp = File(target.parentFile, target.name + ".part")
        try {
            target.parentFile?.mkdirs()
            http.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                if (!response.isSuccessful) return@withContext false
                val digest = MessageDigest.getInstance("SHA-256")
                response.body.byteStream().use { input -> temp.outputStream().use { out -> copy(input, digest) { b, n -> out.write(b, 0, n) } } }
                if (digest.digest().toHex() != sha256) return@withContext false.also { temp.delete() }
            }
            temp.renameTo(target)
        } catch (_: IOException) {
            temp.delete()
            false
        }
    }

    private data class Stat(val size: Long, val modified: Long, val mime: String)

    private fun stat(uri: String): Stat? {
        val parsed = uri.toUri()
        return when (parsed.scheme) {
            "file" -> File(parsed.path.orEmpty()).takeIf { it.isFile }?.let { Stat(it.length(), it.lastModified(), mimeOf(uri)) }
            "content" -> try {
                context.contentResolver.query(parsed, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    val size = cursor.getLong(0).takeIf { it > 0 } ?: return null
                    Stat(size, 0L, context.contentResolver.getType(parsed) ?: mimeOf(uri))
                }
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            else -> null
        }
    }

    private fun open(uri: String): InputStream? = try {
        context.contentResolver.openInputStream(uri.toUri())
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun hash(uri: String): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        return try {
            open(uri)?.use { copy(it, digest) { _, _ -> } } ?: return null
            digest.digest().toHex()
        } catch (_: IOException) {
            null
        }
    }

    private inline fun copy(input: InputStream, digest: MessageDigest, sink: (ByteArray, Int) -> Unit) {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
            sink(buffer, n)
        }
    }

    private fun mediaAllowed(): Boolean {
        if (settings.values.value.syncOnMobileData) return true
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return !connectivity.isActiveNetworkMetered
    }

    private fun fontFile(id: String) = File(File(context.filesDir, FONTS_DIR), id.removePrefix(BundledFont.IMPORTED_PREFIX))
    private fun lutFile(id: String) = File(File(context.filesDir, LUTS_DIR), id.removePrefix(FilterCatalog.CUSTOM_PREFIX) + ".cube")
    private fun cloudFile(ref: CloudRef) = File(File(context.filesDir, CLOUD_DIR), ref.sha256 + (ref.extension?.let { ".$it" } ?: ""))
    private fun fileUri(file: File) = Uri.fromFile(file).toString()

    private fun mimeOf(uri: String): String =
        extensionOf(uri)?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) } ?: OCTET_STREAM

    private fun extensionFor(mime: String): String? = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)

    private fun CloudRef.tail(): String = sha256 + (extension?.let { ".$it" } ?: "")

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        /** Downloaded files (Phase 30D), swept with the app's other media when no project uses them. */
        const val CLOUD_DIR = "cloud"
        private const val FONTS_DIR = "fonts"
        private const val LUTS_DIR = "luts"
        private const val BUFFER_BYTES = 1 shl 20
        private const val OCTET_STREAM = "application/octet-stream"
    }
}
