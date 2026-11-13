package com.savie.precut.auth

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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ErrorBody(val error: ErrorCode)

@Serializable
private data class ErrorCode(val code: String)

/** Only `signed_out` ends the session; `not_registered` (the install's token) doesn't. */
private fun failureFor(text: String): AuthFailure {
    val code = try {
        cloudJson.decodeFromString<ErrorBody>(text).error.code
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
    return when (code) {
        "signed_out" -> AuthFailure.SessionEnded
        "not_configured" -> AuthFailure.NotSetUp
        else -> AuthFailure.Busy
    }
}

/** The account as the server keeps it (GET /account). */
@Serializable
data class CloudAccount(
    val email: String? = null,
    val pro: Boolean = false,
    val storageUsed: Long = 0,
    val storageLimit: Long = 0,
    val maxFileBytes: Long = 0,
    val projects: Int = 0,
)

/**
 * The cloud function's account routes (docs/CLOUD_API.md, Phase 30), each with the signed-in
 * user's access token. [accessToken] callers get from [AccountRepository.accessToken].
 */
@Singleton
class AccountApi @Inject constructor(
    private val config: CloudConfig,
    private val http: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun account(accessToken: String): AuthResult<CloudAccount> =
        send(request("account", accessToken).get().build()) { cloudJson.decodeFromString<CloudAccount>(it) }

    /** Deletes the account, its projects and files on the server. */
    suspend fun delete(accessToken: String): AuthResult<Unit> =
        send(request("account", accessToken).delete().build()) { }

    /** Signs this install (its [installToken]) in to the account, so Pro follows the account. */
    suspend fun link(accessToken: String, installToken: String): AuthResult<Unit> = send(
        request("account/link", accessToken).header("X-Install-Token", installToken).post(ByteArray(0).toRequestBody()).build(),
    ) { }

    private fun request(path: String, accessToken: String): Request.Builder = Request.Builder()
        .url(config.url.trimEnd('/') + "/" + path)
        .header("X-PreCut-Api", API_VERSION.toString())
        .header("Authorization", "Bearer $accessToken")

    private suspend fun <T> send(request: Request, read: (String) -> T): AuthResult<T> = withContext(io) {
        try {
            http.newCall(request).await().use { response ->
                val text = response.body.string()
                if (response.isSuccessful) {
                    AuthResult.Ok(read(text))
                } else {
                    AuthResult.Failed(failureFor(text))
                }
            }
        } catch (_: IOException) {
            AuthResult.Failed(AuthFailure.Offline)
        } catch (_: SerializationException) {
            AuthResult.Failed(AuthFailure.Busy)
        } catch (_: IllegalArgumentException) {
            AuthResult.Failed(AuthFailure.Busy)
        }
    }
}
