package com.savie.precut.data.sync

import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Phase 30D: resumable uploads as Supabase Storage speaks them (TUS 1.0). */
class TusUploadTest {

    private val server = MockWebServer()
    private val tus = TusUpload(OkHttpClient(), Dispatchers.IO)
    private val bytes = ByteArray(7 * 1024 * 1024) { (it % 251).toByte() } // two chunks: 6 MB and 1 MB
    private var created: String? = null

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun file() = TusFile(server.url("/storage/v1/upload/resumable").toString(), "media", "user-1/abc", "video/mp4", bytes.size.toLong()) {
        bytes.inputStream()
    }

    private fun reply(code: Int, vararg headers: String) = server.enqueue(MockResponse(code, Headers.headersOf(*headers), ""))

    private fun next() = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))

    private fun upload(resume: String? = null) = runBlocking {
        tus.upload(file(), "sb_publishable_test", resume, token = { AuthResult.Ok("access") }, onCreated = { created = it })
    }

    @Test
    fun `a new upload is created, then sent in 6 MB chunks`() {
        reply(201, "Location", "/storage/v1/upload/resumable/up-1", "Tus-Resumable", "1.0.0")
        reply(204, "Upload-Offset", "${6 * 1024 * 1024}")
        reply(204, "Upload-Offset", "${bytes.size}")
        assertEquals(TusResult.Done, upload())
        assertEquals(server.url("/storage/v1/upload/resumable/up-1").toString(), created)

        val create = next()
        assertEquals("POST", create.method)
        assertEquals("1.0.0", create.headers["Tus-Resumable"])
        assertEquals("Bearer access", create.headers["Authorization"])
        assertEquals("sb_publishable_test", create.headers["apikey"])
        assertEquals("${bytes.size}", create.headers["Upload-Length"])
        val metadata = create.headers["Upload-Metadata"].orEmpty().split(",").associate { pair ->
            pair.substringBefore(' ') to String(Base64.getDecoder().decode(pair.substringAfter(' ')))
        }
        assertEquals("media", metadata["bucketName"])
        assertEquals("user-1/abc", metadata["objectName"])
        assertEquals("video/mp4", metadata["contentType"])

        val first = next()
        assertEquals("PATCH", first.method)
        assertEquals("0", first.headers["Upload-Offset"])
        assertEquals("application/offset+octet-stream", first.headers["Content-Type"])
        assertEquals(6 * 1024 * 1024L, first.bodySize)
        val second = next()
        assertEquals("${6 * 1024 * 1024}", second.headers["Upload-Offset"])
        assertEquals(1024 * 1024L, second.bodySize)
    }

    @Test
    fun `an upload started before goes on from the server's offset`() {
        val resume = server.url("/storage/v1/upload/resumable/up-2").toString()
        reply(200, "Upload-Offset", "${6 * 1024 * 1024}", "Upload-Length", "${bytes.size}")
        reply(204, "Upload-Offset", "${bytes.size}")
        assertEquals(TusResult.Done, upload(resume))
        assertEquals("HEAD", next().method)
        val patch = next()
        assertEquals("${6 * 1024 * 1024}", patch.headers["Upload-Offset"])
        assertEquals(1024 * 1024L, patch.bodySize)
        assertEquals(null, created)
    }

    @Test
    fun `an expired upload starts again`() {
        reply(404)
        reply(201, "Location", "/storage/v1/upload/resumable/up-3")
        reply(204, "Upload-Offset", "${6 * 1024 * 1024}")
        reply(204, "Upload-Offset", "${bytes.size}")
        assertEquals(TusResult.Done, upload(server.url("/gone").toString()))
        assertEquals("HEAD", next().method)
        assertEquals("POST", next().method)
    }

    @Test
    fun `a file already stored, a refused token and a busy server`() {
        reply(409)
        assertEquals(TusResult.Exists, upload())
        reply(401)
        assertEquals(TusResult.Failed(AuthFailure.SessionEnded), upload())
        reply(201, "Location", "/storage/v1/upload/resumable/up-4")
        reply(503)
        assertEquals(TusResult.Failed(AuthFailure.Busy), upload())
    }
}
