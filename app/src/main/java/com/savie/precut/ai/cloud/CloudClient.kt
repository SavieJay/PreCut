package com.savie.precut.ai.cloud

import com.savie.precut.ai.UploadConsent
import com.savie.precut.data.AppSettings
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.ai.CloudStatus
import com.savie.precut.model.ai.Transcript
import com.savie.precut.model.ai.edit.EditStepReply
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The app's side of the cloud API (docs/CLOUD_API.md). Nothing is sent while cloud AI is off in
 * Settings, in a build without a backend, or offline, and an upload needs an [UploadConsent] for
 * exactly that audio. An install registers on its first upload (opening Settings never does), and
 * once more when the server has forgotten it.
 */
@Singleton
class CloudClient @Inject constructor(
    private val config: CloudConfig,
    private val http: OkHttpClient,
    private val installs: InstallStore,
    private val integrity: IntegrityTokens,
    private val network: NetworkState,
    private val settings: AppSettings,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * This install's free minutes and plan; null before its first upload, when there's nothing to
     * ask. [forPro] asks with cloud AI switched off too: Pro is checked with the same server.
     */
    suspend fun status(forPro: Boolean = false): AiResult<CloudStatus?> {
        blocked(needsSwitch = !forPro)?.let { return AiResult.Failed(it) }
        val token = installs.token ?: return AiResult.Ok(null)
        return when (val reply = send(request("status").header(HEADER_TOKEN, token).get().build())) {
            is Reply.Ok -> decode<StatusResponse>(reply.body)?.let { keep(it.installToken); AiResult.Ok(it.status.toModel()) } ?: busy()
            is Reply.Refused -> if (reply.error.code == NOT_REGISTERED) {
                installs.token = null
                AiResult.Ok(null)
            } else {
                AiResult.Failed(failureFor(reply.error))
            }
            is Reply.Failed -> AiResult.Failed(reply.failure)
        }
    }

    /**
     * [audio] ([mimeType], [durationMs] long) turned into words. [language] is a 2- or 3-letter code,
     * or null to let the provider tell. Throws if [consent] isn't for this audio or was used before.
     */
    suspend fun transcribe(audio: File, mimeType: String, durationMs: Long, language: String?, consent: UploadConsent): AiResult<Transcript> {
        require(consent.spendOn(audio.length())) { "No consent for this upload" }
        blocked(needsSwitch = true)?.let { return AiResult.Failed(it) }
        if (audio.length() > MAX_UPLOAD_BYTES) return AiResult.Failed(AiFailure.TooLarge)
        val bytes = withContext(io) { audio.readBytes() }
        repeat(2) { attempt ->
            val token = installs.token ?: when (val registered = register()) {
                is AiResult.Ok -> registered.value
                is AiResult.Failed -> return registered
            }
            val proof = when (val result = integrity.token(RequestHashes.transcribe(installs.installId(), bytes))) {
                is AiResult.Ok -> result.value
                is AiResult.Failed -> return result
            }
            val url = config.url.toHttpUrl().newBuilder().addPathSegment("transcribe").addQueryParameter("lang", language ?: "auto").build()
            val call = requestTo(url.toString())
                .header(HEADER_TOKEN, token)
                .header(HEADER_INTEGRITY, proof)
                .header(HEADER_DURATION, durationMs.toString())
                .post(bytes.toRequestBody(mimeType.toMediaType()))
                .build()
            when (val reply = send(call)) {
                is Reply.Ok -> return decode<TranscribeResponse>(reply.body)?.let { keep(it.installToken); AiResult.Ok(it.transcript.toModel()) } ?: busy()
                is Reply.Refused -> if (reply.error.code == NOT_REGISTERED && attempt == 0) {
                    installs.token = null
                } else {
                    return AiResult.Failed(failureFor(reply.error))
                }
                is Reply.Failed -> return AiResult.Failed(reply.failure)
            }
        }
        return busy()
    }

    /**
     * One step of an AI edit (Phase 22C): the whole conversation so far ([messages]) for run
     * [runId], and Claude's reply. Only the project's description and the request are sent, never
     * media; the prompt sheet says so before the first request.
     */
    suspend fun editStep(runId: String, messages: JsonArray): AiResult<EditStepReply> {
        blocked(needsSwitch = true)?.let { return AiResult.Failed(it) }
        val bytes = cloudJson.encodeToString(EditRequest.serializer(), EditRequest(runId, messages)).toByteArray()
        if (bytes.size > MAX_EDIT_BYTES) return AiResult.Failed(AiFailure.TooLarge)
        repeat(2) { attempt ->
            val token = installs.token ?: when (val registered = register()) {
                is AiResult.Ok -> registered.value
                is AiResult.Failed -> return registered
            }
            val proof = when (val result = integrity.token(RequestHashes.edit(installs.installId(), bytes))) {
                is AiResult.Ok -> result.value
                is AiResult.Failed -> return result
            }
            val call = request("edit").header(HEADER_TOKEN, token).header(HEADER_INTEGRITY, proof).post(bytes.toRequestBody(JSON)).build()
            when (val reply = send(call)) {
                is Reply.Ok -> return decode<EditResponse>(reply.body)?.let { keep(it.installToken); AiResult.Ok(it.toModel()) } ?: busy()
                is Reply.Refused -> when {
                    reply.error.code == NOT_REGISTERED && attempt == 0 -> installs.token = null
                    // Without a Claude key on the server, AI edits simply aren't on yet.
                    reply.error.code == NOT_CONFIGURED -> return AiResult.Failed(AiFailure.EditNotReady)
                    else -> return AiResult.Failed(failureFor(reply.error))
                }
                is Reply.Failed -> return AiResult.Failed(reply.failure)
            }
        }
        return busy()
    }

    /**
     * PreCut Pro (Phase 25): sends a subscription's [purchaseToken] (after buying, or to restore)
     * for the server to check with Google Play and link to this install; returns the install's
     * status with its plan. Works with cloud AI switched off: no media or text is sent.
     */
    suspend fun purchase(purchaseToken: String): AiResult<CloudStatus> {
        blocked(needsSwitch = false)?.let { return AiResult.Failed(it) }
        val body = cloudJson.encodeToString(PurchaseRequest.serializer(), PurchaseRequest(purchaseToken)).toRequestBody(JSON)
        repeat(2) { attempt ->
            val token = installs.token ?: when (val registered = register()) {
                is AiResult.Ok -> registered.value
                is AiResult.Failed -> return registered
            }
            val proof = when (val result = integrity.token(RequestHashes.purchase(installs.installId(), purchaseToken))) {
                is AiResult.Ok -> result.value
                is AiResult.Failed -> return result
            }
            when (val reply = send(request("purchase").header(HEADER_TOKEN, token).header(HEADER_INTEGRITY, proof).post(body).build())) {
                is Reply.Ok -> return decode<StatusResponse>(reply.body)?.let { keep(it.installToken); AiResult.Ok(it.status.toModel()) } ?: busy()
                is Reply.Refused -> if (reply.error.code == NOT_REGISTERED && attempt == 0) {
                    installs.token = null
                } else {
                    return AiResult.Failed(failureFor(reply.error))
                }
                is Reply.Failed -> return AiResult.Failed(reply.failure)
            }
        }
        return busy()
    }

    /**
     * Deletes what the server keeps about this install, then forgets the install here, so the next
     * upload registers as a new one. Works with cloud AI switched off.
     */
    suspend fun forget(): AiResult<Unit> {
        val token = installs.token
        if (token == null) {
            installs.reset()
            return AiResult.Ok(Unit)
        }
        blocked(needsSwitch = false)?.let { return AiResult.Failed(it) }
        return when (val reply = send(request("me").header(HEADER_TOKEN, token).delete().build())) {
            is Reply.Ok -> AiResult.Ok(Unit).also { installs.reset() }
            // The server already has nothing for this install.
            is Reply.Refused -> if (reply.error.code == NOT_REGISTERED) AiResult.Ok(Unit).also { installs.reset() } else AiResult.Failed(failureFor(reply.error))
            is Reply.Failed -> AiResult.Failed(reply.failure)
        }
    }

    private suspend fun register(): AiResult<String> {
        val installId = installs.installId()
        val proof = when (val result = integrity.token(RequestHashes.register(installId))) {
            is AiResult.Ok -> result.value
            is AiResult.Failed -> return result
        }
        val body = cloudJson.encodeToString(RegisterRequest.serializer(), RegisterRequest(installId)).toRequestBody(JSON)
        return when (val reply = send(request("register").header(HEADER_INTEGRITY, proof).post(body).build())) {
            is Reply.Ok -> decode<RegisterResponse>(reply.body)?.let { installs.token = it.installToken; AiResult.Ok(it.installToken) } ?: busy()
            is Reply.Refused -> AiResult.Failed(failureFor(reply.error))
            is Reply.Failed -> AiResult.Failed(reply.failure)
        }
    }

    /** Why nothing may be sent now, or null when it may. Deleting cloud data doesn't need the switch on. */
    private fun blocked(needsSwitch: Boolean): AiFailure? = when {
        !config.isSetUp -> AiFailure.NotSetUp
        needsSwitch && !settings.values.value.cloudAiEnabled -> AiFailure.CloudOff
        !network.isOnline() -> AiFailure.Offline
        else -> null
    }

    /** A request to one of the function's routes, with the headers every request carries. */
    private fun request(path: String): Request.Builder = requestTo(config.url.trimEnd('/') + "/" + path)

    private fun requestTo(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header(HEADER_API, API_VERSION.toString())
        .apply { if (config.region.isNotBlank()) header(HEADER_REGION, config.region) }

    private fun keep(renewed: String?) {
        if (renewed != null) installs.token = renewed
    }

    private suspend fun send(request: Request): Reply = withContext(io) {
        try {
            http.newCall(request).await().use { response ->
                val body = response.body.string()
                if (response.isSuccessful) {
                    Reply.Ok(body)
                } else {
                    decode<ErrorResponse>(body)?.let { Reply.Refused(it.error) } ?: Reply.Failed(AiFailure.Busy)
                }
            }
        } catch (_: IOException) {
            Reply.Failed(if (network.isOnline()) AiFailure.Busy else AiFailure.Offline)
        }
    }

    private inline fun <reified T> decode(body: String): T? = try {
        cloudJson.decodeFromString<T>(body)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun busy(): AiResult.Failed = AiResult.Failed(AiFailure.Busy)

    private sealed interface Reply {
        data class Ok(val body: String) : Reply
        data class Refused(val error: ErrorDto) : Reply
        data class Failed(val failure: AiFailure) : Reply
    }

    private companion object {
        const val HEADER_API = "X-PreCut-Api"
        const val HEADER_REGION = "x-region"
        const val HEADER_TOKEN = "X-Install-Token"
        const val HEADER_INTEGRITY = "X-Integrity-Token"
        const val HEADER_DURATION = "X-Audio-Duration-Ms"
        const val NOT_REGISTERED = "not_registered"
        const val NOT_CONFIGURED = "not_configured"
        const val MAX_UPLOAD_BYTES = 10L * 1024 * 1024
        /** The server's limit for one step's conversation. */
        const val MAX_EDIT_BYTES = 512 * 1024
        val JSON = "application/json".toMediaType()
    }
}

/** The response, or the call cancelled with the coroutine. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
}
