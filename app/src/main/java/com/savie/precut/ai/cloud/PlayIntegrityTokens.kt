package com.savie.precut.ai.cloud

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import com.savie.precut.data.await
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** A Play Integrity token bound to [requestHash] (docs/CLOUD_API.md), or why there isn't one. */
fun interface IntegrityTokens {
    suspend fun token(requestHash: String): AiResult<String>
}

/**
 * Google Play Integrity's standard requests: prepared once (a few seconds) and kept, prepared
 * again when Google says the provider has expired, and retried briefly on transient errors.
 * Shorter waits than Google's 5/10/20 s suggestion, as someone is waiting on the result.
 */
@Singleton
class PlayIntegrityTokens @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: CloudConfig,
) : IntegrityTokens {
    private val manager by lazy { IntegrityManagerFactory.createStandard(context) }
    private val lock = Mutex()
    private var provider: StandardIntegrityTokenProvider? = null

    override suspend fun token(requestHash: String): AiResult<String> = lock.withLock {
        for (attempt in 0..BACKOFF_MS.size) {
            try {
                return@withLock AiResult.Ok(request(requestHash))
            } catch (e: StandardIntegrityException) {
                Log.w(TAG, "Play Integrity error ${e.errorCode}")
                when (e.errorCode) {
                    StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID -> provider = null
                    in TRANSIENT -> if (attempt < BACKOFF_MS.size) delay(BACKOFF_MS[attempt])
                    else -> return@withLock AiResult.Failed(failureFor(e.errorCode))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything else from Play services (an unexpected ApiException, a binder failure).
                Log.w(TAG, "Play Integrity failed", e)
                return@withLock AiResult.Failed(AiFailure.Busy)
            }
        }
        AiResult.Failed(AiFailure.Busy)
    }

    private suspend fun request(requestHash: String): String {
        val prepared = provider ?: manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(config.playCloudProjectNumber).build(),
        ).await().also { provider = it }
        return prepared.request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()).await().token()
    }

    private fun failureFor(code: Int): AiFailure = when (code) {
        StandardIntegrityErrorCode.NETWORK_ERROR -> AiFailure.Offline
        StandardIntegrityErrorCode.API_NOT_AVAILABLE,
        StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
        StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
        StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE,
        StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED,
        StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED,
        -> AiFailure.NoPlayServices
        StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID -> AiFailure.NotSetUp
        StandardIntegrityErrorCode.APP_NOT_INSTALLED, StandardIntegrityErrorCode.APP_UID_MISMATCH -> AiFailure.AppNotVerified
        else -> AiFailure.Busy
    }

    private companion object {
        const val TAG = "PlayIntegrity"
        val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000)
        val TRANSIENT = setOf(
            StandardIntegrityErrorCode.TOO_MANY_REQUESTS,
            StandardIntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE,
            StandardIntegrityErrorCode.CLIENT_TRANSIENT_ERROR,
            StandardIntegrityErrorCode.INTERNAL_ERROR,
        )
    }
}
