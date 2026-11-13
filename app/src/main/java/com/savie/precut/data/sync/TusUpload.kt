package com.savie.precut.data.sync

import java.util.Base64
import com.savie.precut.ai.cloud.await
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** One file to upload to Storage: where it goes and how to read it from any point. */
class TusFile(
    val endpoint: String,
    val bucket: String,
    val objectName: String,
    val mime: String,
    val size: Long,
    /** A fresh stream from the file's start. */
    val open: () -> InputStream?,
)

sealed interface TusResult {
    data object Done : TusResult
    /** Storage already has an object at that name (an earlier upload finished). */
    data object Exists : TusResult
    data class Failed(val failure: AuthFailure) : TusResult
}

/**
 * Supabase Storage's resumable uploads (the TUS 1.0 protocol, Phase 30D): create the upload, then
 * send 6 MB chunks (the size Storage requires), each with a current access token. [resumeUrl] (an
 * upload this phone started before) continues from the server's offset; [onCreated] gets a new
 * upload's address to keep for that.
 */
@Singleton
class TusUpload @Inject constructor(
    private val http: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun upload(
        file: TusFile,
        apiKey: String,
        resumeUrl: String?,
        token: suspend () -> AuthResult<String>,
        onCreated: suspend (String) -> Unit,
        onProgress: (Long) -> Unit = {},
    ): TusResult = withContext(io) {
        try {
            var url = resumeUrl
            var offset = url?.let { offsetOf(it, file, apiKey, token) } ?: -1L
            if (offset < 0) {
                url = when (val created = create(file, apiKey, token)) {
                    is Created.At -> created.url
                    is Created.Refused -> return@withContext created.result
                }
                onCreated(url)
                offset = 0
            }
            send(checkNotNull(url) { "an upload address" }, offset, file, apiKey, token, onProgress)
        } catch (_: IOException) {
            TusResult.Failed(AuthFailure.Offline)
        }
    }

    private sealed interface Created {
        data class At(val url: String) : Created
        data class Refused(val result: TusResult) : Created
    }

    private suspend fun create(file: TusFile, apiKey: String, token: suspend () -> AuthResult<String>): Created {
        val bearer = when (val t = token()) {
            is AuthResult.Ok -> t.value
            is AuthResult.Failed -> return Created.Refused(TusResult.Failed(t.failure))
        }
        val metadata = listOf("bucketName" to file.bucket, "objectName" to file.objectName, "contentType" to file.mime, "cacheControl" to "3600")
            .joinToString(",") { (key, value) -> "$key ${Base64.getEncoder().encodeToString(value.encodeToByteArray())}" }
        val request = base(file.endpoint, bearer, apiKey)
            .header("Upload-Length", file.size.toString())
            .header("Upload-Metadata", metadata)
            .header("x-upsert", "false")
            .post(ByteArray(0).toRequestBody())
            .build()
        http.newCall(request).await().use { response ->
            return when {
                response.code == CREATED -> response.header("Location")
                    ?.let { Created.At(file.endpoint.toHttpUrl().resolve(it)?.toString() ?: it) }
                    ?: Created.Refused(TusResult.Failed(AuthFailure.Busy))
                response.code == CONFLICT -> Created.Refused(TusResult.Exists)
                else -> Created.Refused(TusResult.Failed(failureFor(response.code)))
            }
        }
    }

    /** The server's offset for an upload, or -1 when it's gone (expired after a day) or unknown. */
    private suspend fun offsetOf(url: String, file: TusFile, apiKey: String, token: suspend () -> AuthResult<String>): Long {
        val bearer = (token() as? AuthResult.Ok)?.value ?: return -1
        http.newCall(base(url, bearer, apiKey).head().build()).await().use { response ->
            if (!response.isSuccessful) return -1
            return response.header("Upload-Offset")?.toLongOrNull()?.takeIf { it in 0..file.size } ?: -1
        }
    }

    private suspend fun send(
        url: String,
        start: Long,
        file: TusFile,
        apiKey: String,
        token: suspend () -> AuthResult<String>,
        onProgress: (Long) -> Unit,
    ): TusResult {
        val stream = file.open() ?: return TusResult.Failed(AuthFailure.Busy)
        stream.use { input ->
            skipFully(input, start)
            var offset = start
            val buffer = ByteArray(CHUNK_BYTES)
            while (offset < file.size) {
                val length = readChunk(input, buffer, minOf(CHUNK_BYTES.toLong(), file.size - offset).toInt())
                if (length <= 0) return TusResult.Failed(AuthFailure.Busy) // the file got shorter
                val bearer = when (val t = token()) {
                    is AuthResult.Ok -> t.value
                    is AuthResult.Failed -> return TusResult.Failed(t.failure)
                }
                val request = base(url, bearer, apiKey)
                    .header("Upload-Offset", offset.toString())
                    .patch(buffer.copyOf(length).toRequestBody(OFFSET_STREAM))
                    .build()
                http.newCall(request).await().use { response ->
                    if (response.code != NO_CONTENT) return TusResult.Failed(failureFor(response.code))
                    offset = response.header("Upload-Offset")?.toLongOrNull() ?: (offset + length)
                }
                onProgress(offset)
            }
        }
        return TusResult.Done
    }

    private fun base(url: String, bearer: String, apiKey: String) = Request.Builder()
        .url(url)
        .header("Tus-Resumable", TUS_VERSION)
        .header("Authorization", "Bearer $bearer")
        .header("apikey", apiKey)

    private fun failureFor(code: Int): AuthFailure = when (code) {
        401 -> AuthFailure.SessionEnded
        429 -> AuthFailure.TooMany
        else -> AuthFailure.Busy
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) {
                if (input.read() < 0) throw IOException("file shorter than its upload")
                left--
            } else {
                left -= skipped
            }
        }
    }

    /** Fills [buffer] up to [length] bytes (streams may return less per read). */
    private fun readChunk(input: InputStream, buffer: ByteArray, length: Int): Int {
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private companion object {
        const val TUS_VERSION = "1.0.0"
        const val CHUNK_BYTES = 6 * 1024 * 1024
        const val CREATED = 201
        const val NO_CONTENT = 204
        const val CONFLICT = 409
        val OFFSET_STREAM = "application/offset+octet-stream".toMediaType()
    }
}
