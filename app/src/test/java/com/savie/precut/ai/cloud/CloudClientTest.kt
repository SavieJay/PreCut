package com.savie.precut.ai.cloud

import com.savie.precut.ai.UploadConsent
import com.savie.precut.data.InMemoryAppSettings
import com.savie.precut.data.SettingsValues
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiFeature
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.ai.CloudStatus
import com.savie.precut.model.ai.Transcript
import com.savie.precut.model.ai.TranscriptWord
import com.savie.precut.model.ai.UploadSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.junit.After
import com.savie.precut.model.pro.PlanState
import com.savie.precut.model.pro.ProPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** The cloud client against a local server speaking docs/CLOUD_API.md. */
class CloudClientTest {

    private val server = MockWebServer()
    private val installs = MemoryInstalls()
    private val integrity = FakeIntegrity()
    private val settings = InMemoryAppSettings()
    private var online = true
    private lateinit var audio: File

    @Before
    fun setUp() {
        server.start()
        audio = File.createTempFile("cloud", ".wav").apply { writeBytes(AUDIO) }
    }

    @After
    fun tearDown() {
        server.close()
        audio.delete()
    }

    private fun client(url: String = server.url("/functions/v1/cloud").toString()) =
        CloudClient(CloudConfig(url, "eu-west-1", 989_668_249_896L), OkHttpClient(), installs, integrity, { online }, settings, Dispatchers.IO)

    private fun consent() = UploadConsent.givenFor(UploadSummary(AiFeature.CHECK, 2_000L, AUDIO.size.toLong()))

    private fun transcribe(client: CloudClient = client()) = runBlocking { client.transcribe(audio, "audio/wav", 2_000L, "en", consent()) }

    private fun reply(code: Int, body: String) = server.enqueue(MockResponse(code, Headers.headersOf("Content-Type", "application/json"), body))

    private fun next() = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request was made" }

    @Test
    fun `the first upload registers this install, then sends the audio bound to both proofs`() {
        reply(200, """{"installToken":"tok-1","status":$STATUS}""")
        reply(200, """{"transcript":{"language":"en","durationMs":2400,"words":[{"text":"Hello","startMs":100,"endMs":600}]},"secondsLeft":1797}""")

        assertEquals(AiResult.Ok(Transcript("en", 2_400L, listOf(TranscriptWord("Hello", 100L, 600L)))), transcribe())

        val installId = installs.installId()
        val register = next()
        assertEquals("POST", register.method)
        assertEquals("/functions/v1/cloud/register", register.url.encodedPath)
        assertEquals("""{"installId":"$installId"}""", register.body?.utf8())
        assertEquals("1", register.headers["X-PreCut-Api"])
        assertEquals("eu-west-1", register.headers["x-region"])
        assertEquals("proof:${RequestHashes.register(installId)}", register.headers["X-Integrity-Token"])

        val upload = next()
        assertEquals("/functions/v1/cloud/transcribe", upload.url.encodedPath)
        assertEquals("en", upload.url.queryParameter("lang"))
        assertEquals("tok-1", upload.headers["X-Install-Token"])
        assertEquals("proof:${RequestHashes.transcribe(installId, AUDIO)}", upload.headers["X-Integrity-Token"])
        assertEquals("2000", upload.headers["X-Audio-Duration-Ms"])
        assertEquals("audio/wav", upload.headers["Content-Type"])
        assertEquals(AUDIO.toList(), upload.body?.toByteArray()?.toList())
        assertEquals("tok-1", installs.token)
    }

    @Test
    fun `an install the server forgot registers again, once`() {
        installs.token = "old"
        reply(401, """{"error":{"code":"not_registered"}}""")
        reply(200, """{"installToken":"tok-2","status":$STATUS}""")
        reply(401, """{"error":{"code":"not_registered"}}""")

        assertEquals(AiResult.Failed(AiFailure.Busy), transcribe())
        assertEquals(listOf("/transcribe", "/register", "/transcribe"), List(3) { next().url.encodedPath.removePrefix("/functions/v1/cloud") })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `refusals become failures the app can explain`() {
        installs.token = "tok"
        val cases = listOf(
            """{"error":{"code":"quota_used","secondsLeft":40,"resetsAt":"2026-11-01T00:00:00+00:00"}}""" to AiFailure.QuotaUsed(40, NOV_1),
            """{"error":{"code":"budget_reached","resetsAt":"2026-11-01T00:00:00+00:00"}}""" to AiFailure.BudgetReached(NOV_1),
            """{"error":{"code":"paused"}}""" to AiFailure.Paused,
            """{"error":{"code":"too_long","maxUploadSeconds":600}}""" to AiFailure.TooLong(600),
            """{"error":{"code":"device_not_verified"}}""" to AiFailure.DeviceNotVerified,
            """{"error":{"code":"app_not_verified"}}""" to AiFailure.AppNotVerified,
            """{"error":{"code":"bad_audio"}}""" to AiFailure.BadAudio,
            """{"error":{"code":"upgrade_required"}}""" to AiFailure.UpdateApp,
            """{"error":{"code":"provider_failed"}}""" to AiFailure.Busy,
            """<html>Bad gateway</html>""" to AiFailure.Busy,
        )
        for ((body, failure) in cases) {
            reply(if (body.startsWith("<")) 502 else 400, body)
            assertEquals(body, AiResult.Failed(failure), transcribe())
        }
    }

    @Test
    fun `nothing is sent offline, switched off, or in a build without a backend`() {
        online = false
        assertEquals(AiResult.Failed(AiFailure.Offline), transcribe())
        online = true
        settings.update { it.copy(cloudAiEnabled = false) }
        assertEquals(AiResult.Failed(AiFailure.CloudOff), transcribe())
        settings.update { it.copy(cloudAiEnabled = true) }
        assertEquals(AiResult.Failed(AiFailure.NotSetUp), transcribe(client(url = "")))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an upload needs a consent for exactly this audio, used once`() {
        val client = client()
        val forOtherAudio = UploadConsent.givenFor(UploadSummary(AiFeature.CHECK, 2_000L, AUDIO.size + 1L))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.transcribe(audio, "audio/wav", 2_000L, "en", forOtherAudio) } }
        installs.token = "tok"
        reply(400, """{"error":{"code":"paused"}}""")
        val consent = consent()
        runBlocking { client.transcribe(audio, "audio/wav", 2_000L, "en", consent) }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.transcribe(audio, "audio/wav", 2_000L, "en", consent) } }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `when Play Integrity can't vouch for the phone, nothing is uploaded`() {
        integrity.failure = AiFailure.DeviceNotVerified
        assertEquals(AiResult.Failed(AiFailure.DeviceNotVerified), transcribe())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `status asks nothing before the first upload, then reads the minutes and keeps a renewed token`() {
        assertEquals(AiResult.Ok(null), runBlocking { client().status() })
        assertEquals(0, server.requestCount)

        installs.token = "tok"
        reply(200, """{"status":$STATUS,"installToken":"tok-renewed"}""")
        assertEquals(AiResult.Ok(CloudStatus(true, 1800, 1800, NOV_1, 600)), runBlocking { client().status() })
        assertEquals("tok", next().headers["X-Install-Token"])
        assertEquals("tok-renewed", installs.token)
    }

    @Test
    fun `forget deletes on the server, even switched off, and the next upload is a new install`() {
        installs.token = "tok"
        val before = installs.installId()
        settings.update { it.copy(cloudAiEnabled = false) }
        reply(200, """{"deleted":true}""")
        assertEquals(AiResult.Ok(Unit), runBlocking { client().forget() })
        val request = next()
        assertEquals("DELETE", request.method)
        assertEquals("/functions/v1/cloud/me", request.url.encodedPath)
        assertNull(installs.token)
        assertNotEquals(before, installs.installId())
    }

    @Test
    fun `a connection that fails while online is worth retrying, not offline`() {
        installs.token = "tok"
        val url = server.url("/functions/v1/cloud").toString()
        server.close()
        assertEquals(AiResult.Failed(AiFailure.Busy), transcribe(client(url)))
    }

    @Test
    fun `settings saved before cloud AI existed come back with it switched on`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        assertEquals(true, json.decodeFromString(SettingsValues.serializer(), """{"photoDurationMs":3000}""").cloudAiEnabled)
    }

    private class MemoryInstalls : InstallStore {
        private var id: String? = null
        override fun installId(): String = id ?: UUID.randomUUID().toString().also { id = it }
        override var token: String? = null
        override fun reset() {
            id = null
            token = null
        }
    }

    /** Vouches with a token naming the hash it was asked for, unless told to fail. */
    @Test
    fun `a purchase is sent bound to its token, works with cloud AI off, and the plan comes back`() {
        installs.token = "tok"
        settings.update { it.copy(cloudAiEnabled = false) }
        val status = """{"cloudEnabled":true,"secondsLeft":18000,"secondsPerMonth":18000,"resetsAt":"2026-11-01T00:00:00+00:00","maxUploadSeconds":600,""" +
            """"pro":{"pro":true,"state":"active","basePlanId":"yearly","expiresAt":"2026-11-01T00:00:00+00:00","autoRenewing":true,"trial":false}}"""
        reply(200, """{"status":$status}""")
        val result = runBlocking { client().purchase("purchase-token-123") }
        val plan = (result as AiResult.Ok).value.plan
        assertEquals(ProPlan(pro = true, state = PlanState.ACTIVE, basePlanId = "yearly", expiresAtMs = NOV_1, autoRenewing = true), plan)
        val request = next()
        assertEquals("/functions/v1/cloud/purchase", request.url.encodedPath)
        assertEquals("proof:${RequestHashes.purchase(installs.installId(), "purchase-token-123")}", request.headers["X-Integrity-Token"])
        reply(400, """{"error":{"code":"bad_purchase"}}""")
        assertEquals(AiResult.Failed(AiFailure.BadPurchase), runBlocking { client().purchase("purchase-token-123") })
    }

    private class FakeIntegrity : IntegrityTokens {
        var failure: AiFailure? = null
        override suspend fun token(requestHash: String): AiResult<String> =
            failure?.let { AiResult.Failed(it) } ?: AiResult.Ok("proof:$requestHash")
    }

    private companion object {
        val AUDIO = byteArrayOf(82, 73, 70, 70, 1, 2, 3, 4, 5, 6, 7, 8)
        const val STATUS = """{"cloudEnabled":true,"secondsLeft":1800,"secondsPerMonth":1800,"resetsAt":"2026-11-01T00:00:00+00:00","maxUploadSeconds":600}"""
        const val NOV_1 = 1_793_491_200_000L
    }
}
