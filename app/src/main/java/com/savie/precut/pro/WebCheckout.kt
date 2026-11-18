package com.savie.precut.pro

import android.content.Context
import androidx.core.content.edit
import com.savie.precut.BuildConfig
import com.savie.precut.ai.cloud.API_VERSION
import com.savie.precut.ai.cloud.CloudConfig
import com.savie.precut.ai.cloud.await
import com.savie.precut.ai.cloud.cloudJson
import com.savie.precut.auth.AccessTokens
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** How a confirmation came out. */
enum class WebPayment { Paid, NotPaid, NothingPending }

/**
 * PreCut Pro on the website APK (Phase 31): Paystack's payment page, opened in the browser, and
 * the server's check of the payment when the app comes back. Off in Play builds
 * ([BuildConfig.WEB_PAYMENTS] is false there, so R8 removes this path).
 */
@Singleton
class WebCheckout @Inject constructor(
    @ApplicationContext context: Context,
    private val config: CloudConfig,
    private val http: OkHttpClient,
    private val tokens: AccessTokens,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** This build sells Pro through Paystack, and someone is signed in to buy it. */
    val available: Boolean get() = BuildConfig.WEB_PAYMENTS && tokens.userId != null

    /** A payment page for [basePlanId] ("monthly" or "yearly"); its address, to open in the browser. */
    suspend fun start(basePlanId: String): AuthResult<String> {
        val body = cloudJson.encodeToString(Checkout.serializer(), Checkout(basePlanId))
        return when (val result = post("paystack/checkout", body) { cloudJson.decodeFromString<Page>(it) }) {
            is AuthResult.Ok -> {
                prefs.edit { putString(KEY_PENDING, result.value.reference) }
                AuthResult.Ok(result.value.url)
            }
            is AuthResult.Failed -> result
        }
    }

    /** After the browser: asks the server whether the pending payment went through. */
    suspend fun confirm(): AuthResult<WebPayment> {
        val reference = prefs.getString(KEY_PENDING, null) ?: return AuthResult.Ok(WebPayment.NothingPending)
        val body = cloudJson.encodeToString(Confirm.serializer(), Confirm(reference))
        return when (val result = post("paystack/confirm", body) { cloudJson.decodeFromString<Confirmed>(it) }) {
            is AuthResult.Ok -> {
                if (result.value.paid) prefs.edit { remove(KEY_PENDING) }
                AuthResult.Ok(if (result.value.paid) WebPayment.Paid else WebPayment.NotPaid)
            }
            is AuthResult.Failed -> result
        }
    }

    /** Whether a payment page was opened and not confirmed yet. */
    val pending: Boolean get() = prefs.getString(KEY_PENDING, null) != null

    /** Stops renewing; Pro lasts until the paid period ends. */
    suspend fun cancel(): AuthResult<Unit> = post("paystack/cancel", "{}") { }

    private suspend fun <T> post(path: String, body: String, read: (String) -> T): AuthResult<T> = tokens.withToken { token ->
        val request = Request.Builder()
            .url(config.url.trimEnd('/') + "/" + path)
            .header("X-PreCut-Api", API_VERSION.toString())
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(JSON))
            .build()
        withContext(io) {
            try {
                http.newCall(request).await().use { response ->
                    val text = response.body.string()
                    when {
                        response.isSuccessful -> AuthResult.Ok(read(text))
                        response.code == 401 -> AuthResult.Failed(AuthFailure.SessionEnded)
                        else -> AuthResult.Failed(AuthFailure.Busy)
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

    @Serializable
    private data class Checkout(val plan: String)

    @Serializable
    private data class Page(val url: String, val reference: String)

    @Serializable
    private data class Confirm(val reference: String)

    @Serializable
    private data class Confirmed(val paid: Boolean)

    private companion object {
        const val PREFS = "pro_web"
        const val KEY_PENDING = "pending_reference"
        val JSON = "application/json".toMediaType()
    }
}
