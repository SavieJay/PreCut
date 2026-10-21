package com.savie.precut.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.SharedMediaStore
import com.savie.precut.model.mapMediaUris
import com.savie.precut.model.mediaUris
import com.savie.precut.model.template.ProjectTemplate
import com.savie.precut.model.template.SLOT_URI_PREFIX
import com.savie.precut.model.totalDurationMs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** A template on the phone, with the pictures that show it. */
data class StoredTemplate(val template: ProjectTemplate, val thumbnailUri: String?, val previewUri: String?) {
    val slotCount: Int get() = template.slots.size
    val durationMs: Long get() = template.project.totalDurationMs()
}

interface TemplateStore {
    /** Every readable template, newest first. */
    suspend fun list(): List<StoredTemplate>

    suspend fun get(id: String): StoredTemplate?

    /** Saves [template], first copying the media it uses (all but its slots) into its own folder. */
    suspend fun save(template: ProjectTemplate): StoredTemplate

    /** Keeps [preview] (a rendered video, moved in) and/or [thumbnail] with template [id]. */
    suspend fun attach(id: String, preview: File? = null, thumbnail: Bitmap? = null)

    suspend fun delete(id: String)

    /** Template [id] ready to fill: its own media copied into the project media folder, so deleting the template never breaks a project. */
    suspend fun forUse(id: String): ProjectTemplate?
}

/**
 * Templates in `files/templates/<id>/`: `template.json`, `thumbnail.jpg`, `preview.mp4` and the
 * template's own media in `media/`. Outside the media sweep's folders, so nothing there is swept.
 */
@Singleton
class FileTemplateStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val projectMedia: SharedMediaStore,
) : TemplateStore {
    private val root get() = File(context.filesDir, DIR)

    override suspend fun list(): List<StoredTemplate> = withContext(ioDispatcher) {
        root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull(::read).sortedByDescending { it.template.createdAt }
    }

    override suspend fun get(id: String): StoredTemplate? = withContext(ioDispatcher) { read(File(root, id)) }

    override suspend fun save(template: ProjectTemplate): StoredTemplate = withContext(ioDispatcher) {
        val dir = File(root, template.id).apply { mkdirs() }
        val media = File(dir, MEDIA).apply { mkdirs() }
        val copies = template.project.mediaUris().filterNot { it.startsWith(SLOT_URI_PREFIX) }.distinct().associateWith { copy(it, media) ?: it }
        val stored = template.copy(project = template.project.mapMediaUris { copies[it] ?: it })
        File(dir, JSON).writeText(TemplateSerializer.encode(stored))
        StoredTemplate(stored, thumbnailUri = null, previewUri = null)
    }

    override suspend fun attach(id: String, preview: File?, thumbnail: Bitmap?) = withContext(ioDispatcher) {
        val dir = File(root, id).takeIf { it.isDirectory } ?: return@withContext
        preview?.let { file ->
            val target = File(dir, PREVIEW)
            if (!file.renameTo(target)) {
                file.copyTo(target, overwrite = true)
                file.delete()
            }
        }
        thumbnail?.let { bitmap -> File(dir, THUMBNAIL).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) } }
        Unit
    }

    override suspend fun delete(id: String) {
        withContext(ioDispatcher) { File(root, id).deleteRecursively() }
    }

    override suspend fun forUse(id: String): ProjectTemplate? {
        val template = get(id)?.template ?: return null
        val own = Uri.fromFile(File(root, id)).toString()
        val copies = template.project.mediaUris().filter { it.startsWith(own) }.distinct().associateWith { projectMedia.copyIn(listOf(it)).firstOrNull() ?: it }
        return template.copy(project = template.project.mapMediaUris { copies[it] ?: it })
    }

    private fun read(dir: File): StoredTemplate? {
        val text = File(dir, JSON).takeIf { it.isFile }?.readText() ?: return null
        val template = TemplateSerializer.decode(text) ?: run {
            Log.w(TAG, "Unreadable template in ${dir.name} (corrupt, or made by a newer PreCut)")
            return null
        }
        fun uriOf(name: String) = File(dir, name).takeIf { it.isFile }?.let { Uri.fromFile(it).toString() }
        return StoredTemplate(template, uriOf(THUMBNAIL), uriOf(PREVIEW))
    }

    /** [uri]'s file copied into [dir] (named by its Uri, so a file used twice is kept once); null when unreadable. */
    private fun copy(uri: String, dir: File): String? {
        val source = uri.toUri()
        val resolver = context.contentResolver
        val extension = resolver.getType(source)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: source.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
            ?: "bin"
        val name = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        val target = File(dir, "$name.$extension")
        return try {
            val input = resolver.openInputStream(source) ?: return null
            input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
            Uri.fromFile(target).toString()
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't copy $uri into a template", e)
            target.delete()
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to $uri for a template", e)
            target.delete()
            null
        }
    }

    private companion object {
        const val TAG = "TemplateStore"
        const val DIR = "templates"
        const val MEDIA = "media"
        const val JSON = "template.json"
        const val THUMBNAIL = "thumbnail.jpg"
        const val PREVIEW = "preview.mp4"
        const val JPEG_QUALITY = 85
    }
}
