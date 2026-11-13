package com.savie.precut.data.sync

import com.savie.precut.ai.cloud.API_VERSION
import com.savie.precut.ai.cloud.CloudConfig
import com.savie.precut.ai.cloud.await
import com.savie.precut.ai.cloud.cloudJson
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A project as the server sends it (docs/CLOUD_API.md, `CloudProject`). */
@Serializable
data class RemoteProject(
    val id: String,
    val revision: Int,
    val name: String = "",
    val project: JsonObject? = null,
    val deleted: Boolean = false,
    val media: List<String> = emptyList(),
)

@Serializable
data class Cursor(val since: String, val after: String)

@Serializable
data class ChangesPage(val projects: List<RemoteProject> = emptyList(), val next: Cursor? = null, val more: Boolean = false)

/** What a save sends: the project JSON, its files' hashes (Phase 30D), and the revision it builds on. */
@Serializable
data class ProjectUpload(
    val baseRevision: Int?,
    val name: String,
    val project: JsonObject,
    val media: List<String>,
    val coverSha256: String?,
    val durationMs: Long,
    val editedAt: String,
)

sealed interface SaveReply {
    data class Saved(val revision: Int) : SaveReply
    /** Changed on the server since [ProjectUpload.baseRevision]; [server] is its copy (null: it has none). */
    data class Conflict(val server: RemoteProject?) : SaveReply
    data class MissingMedia(val missing: List<String>) : SaveReply
    data object TooLarge : SaveReply
}

sealed interface GrantReply {
    /** The account already has the file. */
    data object Exists : GrantReply
    /** Upload it (resumable) to [endpoint], bucket [bucket], at [path]. */
    data class Upload(val endpoint: String, val bucket: String, val path: String) : GrantReply
    data class StorageFull(val used: Long, val limit: Long) : GrantReply
    data object TooLarge : GrantReply
}

enum class ConfirmReply { Ok, NotFound, SizeMismatch }

/** The project and file routes, each with the signed-in user's access token. */
interface ProjectSyncApi {
    suspend fun changes(token: String, cursor: Cursor?): AuthResult<ChangesPage>
    suspend fun save(token: String, id: String, upload: ProjectUpload): AuthResult<SaveReply>
    suspend fun delete(token: String, id: String, base: Int): AuthResult<SaveReply>
    suspend fun grant(token: String, sha256: String, size: Long, mime: String): AuthResult<GrantReply>
    suspend fun confirm(token: String, sha256: String): AuthResult<ConfirmReply>

    /** A signed link that downloads the file for an hour; null when the account hasn't got it. */
    suspend fun downloadUrl(token: String, sha256: String): AuthResult<String?>
}

@Singleton
class CloudProjectSyncApi @Inject constructor(
    private val config: CloudConfig,
    private val http: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ProjectSyncApi {

    override suspend fun changes(token: String, cursor: Cursor?): AuthResult<ChangesPage> {
        val url = route("projects").newBuilder().apply {
            if (cursor != null) addQueryParameter("since", cursor.since).addQueryParameter("after", cursor.after)
        }.build()
        return send(request(url.toString(), token).get().build()) { status, body ->
            if (status == 200) AuthResult.Ok(cloudJson.decodeFromString<ChangesPage>(body)) else null
        }
    }

    override suspend fun save(token: String, id: String, upload: ProjectUpload): AuthResult<SaveReply> {
        val body = cloudJson.encodeToString(ProjectUpload.serializer(), upload).toRequestBody(JSON)
        return send(request(route("projects/$id").toString(), token).put(body).build(), ::saveReply)
    }

    override suspend fun delete(token: String, id: String, base: Int): AuthResult<SaveReply> {
        val url = route("projects/$id").newBuilder().addQueryParameter("baseRevision", base.toString()).build()
        return send(request(url.toString(), token).delete().build(), ::saveReply)
    }

    override suspend fun grant(token: String, sha256: String, size: Long, mime: String): AuthResult<GrantReply> {
        val body = cloudJson.encodeToString(GrantRequest.serializer(), GrantRequest(sha256, size, mime)).toRequestBody(JSON)
        return send(request(route("media/grant").toString(), token).post(body).build()) { status, text ->
            when (status) {
                200 -> cloudJson.decodeFromString<Granted>(text).let { granted ->
                    val upload = granted.upload
                    AuthResult.Ok(if (granted.exists || upload == null) GrantReply.Exists else GrantReply.Upload(upload.url, upload.bucket, upload.path))
                }
                507 -> cloudJson.decodeFromString<Full>(text).error.let { AuthResult.Ok(GrantReply.StorageFull(it.storageUsed, it.storageLimit)) }
                413 -> AuthResult.Ok(GrantReply.TooLarge)
                else -> null
            }
        }
    }

    override suspend fun confirm(token: String, sha256: String): AuthResult<ConfirmReply> {
        val body = cloudJson.encodeToString(ConfirmRequest.serializer(), ConfirmRequest(sha256)).toRequestBody(JSON)
        return send(request(route("media/confirm").toString(), token).post(body).build()) { status, _ ->
            when (status) {
                200 -> AuthResult.Ok(ConfirmReply.Ok)
                404 -> AuthResult.Ok(ConfirmReply.NotFound)
                409 -> AuthResult.Ok(ConfirmReply.SizeMismatch)
                else -> null
            }
        }
    }

    override suspend fun downloadUrl(token: String, sha256: String): AuthResult<String?> =
        send(request(route("media/$sha256").toString(), token).get().build()) { status, text ->
            when (status) {
                200 -> AuthResult.Ok(cloudJson.decodeFromString<Link>(text).url)
                404 -> AuthResult.Ok(null)
                else -> null
            }
        }

    private fun saveReply(status: Int, body: String): AuthResult<SaveReply>? = when (status) {
        200 -> cloudJson.decodeFromString<Saved>(body).revision?.let { AuthResult.Ok(SaveReply.Saved(it)) }
            ?: AuthResult.Ok(SaveReply.Saved(0))
        409 -> cloudJson.decodeFromString<Refusal>(body).error.let { error ->
            when (error.code) {
                "conflict" -> AuthResult.Ok(SaveReply.Conflict(error.project))
                "missing_media" -> AuthResult.Ok(SaveReply.MissingMedia(error.missing))
                else -> null
            }
        }
        413 -> AuthResult.Ok(SaveReply.TooLarge)
        else -> null
    }

    private fun route(path: String) = (config.url.trimEnd('/') + "/" + path).toHttpUrl()

    private fun request(url: String, token: String) = Request.Builder()
        .url(url)
        .header("X-PreCut-Api", API_VERSION.toString())
        .header("Authorization", "Bearer $token")

    /** [read] turns an answer into a result; null for one it doesn't expect (signed out, busy). */
    private suspend fun <T> send(request: Request, read: (Int, String) -> AuthResult<T>?): AuthResult<T> = withContext(io) {
        try {
            http.newCall(request).await().use { response ->
                val body = response.body.string()
                read(response.code, body) ?: AuthResult.Failed(if (response.code == 401) AuthFailure.SessionEnded else AuthFailure.Busy)
            }
        } catch (_: IOException) {
            AuthResult.Failed(AuthFailure.Offline)
        } catch (_: SerializationException) {
            AuthResult.Failed(AuthFailure.Busy)
        } catch (_: IllegalArgumentException) {
            AuthResult.Failed(AuthFailure.Busy)
        }
    }

    @Serializable
    private data class Saved(val revision: Int? = null)

    @Serializable
    private data class GrantRequest(val sha256: String, val size: Long, val mime: String)

    @Serializable
    private data class Granted(val exists: Boolean = false, val upload: UploadTarget? = null)

    @Serializable
    private data class UploadTarget(val url: String, val bucket: String, val path: String)

    @Serializable
    private data class Full(val error: FullBody)

    @Serializable
    private data class FullBody(val storageUsed: Long = 0, val storageLimit: Long = 0)

    @Serializable
    private data class ConfirmRequest(val sha256: String)

    @Serializable
    private data class Link(val url: String)

    @Serializable
    private data class Refusal(val error: RefusalBody)

    @Serializable
    private data class RefusalBody(val code: String, val project: RemoteProject? = null, val missing: List<String> = emptyList())

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
