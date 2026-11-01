package com.savie.precut.update

import com.savie.precut.ai.cloud.CloudConfig
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.update.AppConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The update rules from the cloud function's public `GET /config` (docs/CLOUD_API.md). No install
 * token, no Play Integrity, and not tied to the cloud AI switch: every copy must learn when it's
 * too old. Null on any failure; the caller then keeps what it had.
 */
@Singleton
class AppConfigSource @Inject constructor(
    private val config: CloudConfig,
    http: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    // A quick answer or none: a slow network must not hold up anything.
    private val client = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(): AppConfig? {
        if (config.url.isBlank()) return null
        val request = Request.Builder().url(config.url.trimEnd('/') + "/config?platform=android").get().build()
        return withContext(io) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    json.decodeFromString(AppConfig.serializer(), response.body.string())
                }
            } catch (_: IOException) {
                null
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
