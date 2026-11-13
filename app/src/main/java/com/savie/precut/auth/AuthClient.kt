package com.savie.precut.auth

import com.savie.precut.ai.cloud.await
import com.savie.precut.ai.cloud.cloudJson
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import com.savie.precut.model.account.Session
import com.savie.precut.model.account.authFailureFor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where accounts live (Phase 30): the Supabase project behind the cloud function, its publishable
 * key (public by design, for Auth only) and Google's web client ID. Without the keys sign-in is off.
 */
data class AuthConfig(val supabaseUrl: String, val publishableKey: String, val googleWebClientId: String) {
    val isSetUp: Boolean get() = supabaseUrl.isNotBlank() && publishableKey.isNotBlank()
    val googleSetUp: Boolean get() = isSetUp && googleWebClientId.isNotBlank()

    companion object {
        /** The project's address from the function's (`https://<ref>.supabase.co/functions/v1/cloud`). */
        fun fromCloudUrl(cloudUrl: String, publishableKey: String, googleWebClientId: String) = AuthConfig(
            supabaseUrl = cloudUrl.trimEnd('/').substringBefore("/functions/v1", missingDelimiterValue = ""),
            publishableKey = publishableKey,
            googleWebClientId = googleWebClientId,
        )
    }
}

/** Supabase Auth over its REST API (no SDK): email codes, Google ID tokens, refresh, sign-out. */
@Singleton
class AuthClient @Inject constructor(
    private val config: AuthConfig,
    private val http: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Emails a 6-digit code; makes the account on first use. */
    suspend fun sendCode(email: String): AuthResult<Unit> =
        post("otp", buildJsonObject { put("email", email); put("create_user", true) }) { }

    suspend fun verifyCode(email: String, code: String): AuthResult<Session> =
        post("verify", buildJsonObject { put("type", "email"); put("email", email); put("token", code) }, read = ::session)

    /** [idToken] from Google, made with the SHA-256 of [rawNonce]. */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthResult<Session> = post(
        "token?grant_type=id_token",
        buildJsonObject { put("provider", "google"); put("id_token", idToken); put("nonce", rawNonce) },
        read = ::session,
    )

    suspend fun refresh(refreshToken: String): AuthResult<Session> =
        post("token?grant_type=refresh_token", buildJsonObject { put("refresh_token", refreshToken) }, read = ::session)

    /** Ends the session on the server too (its refresh token stops working). */
    suspend fun signOut(accessToken: String): AuthResult<Unit> =
        post("logout", JsonObject(emptyMap()), accessToken) { }

    private suspend fun <T> post(
        path: String,
        body: JsonObject,
        bearer: String? = null,
        read: (String) -> T?,
    ): AuthResult<T> {
        if (!config.isSetUp) return AuthResult.Failed(AuthFailure.NotSetUp)
        val request = Request.Builder()
            .url("${config.supabaseUrl}/auth/v1/$path")
            .header("apikey", config.publishableKey)
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .post(body.toString().toRequestBody(JSON))
            .build()
        return withContext(io) {
            try {
                http.newCall(request).await().use { response ->
                    val text = response.body.string()
                    if (response.isSuccessful) {
                        read(text)?.let { AuthResult.Ok(it) } ?: AuthResult.Failed(AuthFailure.Busy)
                    } else {
                        AuthResult.Failed(authFailureFor(response.code, decode<AuthError>(text)?.errorCode))
                    }
                }
            } catch (_: IOException) {
                AuthResult.Failed(AuthFailure.Offline)
            }
        }
    }

    private fun session(text: String): Session? = decode<SessionDto>(text)?.let {
        Session(it.accessToken, it.refreshToken, it.expiresAt, it.user.id, it.user.email)
    }

    private inline fun <reified T> decode(text: String): T? = try {
        cloudJson.decodeFromString<T>(text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

@Serializable
private data class SessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
    val user: UserDto,
)

@Serializable
private data class UserDto(val id: String, val email: String? = null)

@Serializable
private data class AuthError(@SerialName("error_code") val errorCode: String? = null)
