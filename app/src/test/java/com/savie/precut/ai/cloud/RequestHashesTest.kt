package com.savie.precut.ai.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The expected values come from the server's own code (supabase/functions/_shared/request_hash.ts,
 * run with Deno), so the app and the server agree on every request hash.
 */
class RequestHashesTest {

    @Test
    fun `the registration hash matches the server's`() {
        assertEquals("N_p842vnKnZ9Xt2u0lK0AQBy_d2y1y5W5xYW-rBb_Wg", RequestHashes.register(INSTALL))
    }

    @Test
    fun `the upload hash binds the install and the exact audio, as the server computes it`() {
        assertEquals("anLP5KR83Sjoy15TzJ53esgjgYhGx59vrre4KchY3Zk", RequestHashes.transcribe(INSTALL, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertEquals("Z0q_EB9us7XczEMwltQCcIlEC3rXpRzBkcJvUz6jN8c", RequestHashes.transcribe(INSTALL, ByteArray(0)))
    }

    @Test
    fun `the AI edit, purchase and translation hashes match the server's`() {
        assertEquals("NnxvaK91A1V6yd59eOkrNQZs_QvJlQrg9-TIB7xrkZo", RequestHashes.edit(INSTALL, """{"runId":"r","messages":[]}""".toByteArray()))
        assertEquals("RgzR5WuTU94TxfJjfvU48HQ6bZ-6qHakMSgqbpGPaZo", RequestHashes.purchase(INSTALL, "purchase-token-123"))
        assertEquals("6JzPQOdYxb0fvDbNB5Y7LYQX9VIr6wREj726KXfC1Ws", RequestHashes.translate(INSTALL, """{"lines":[],"target":"fr"}""".toByteArray()))
        assertEquals("_17AulEVF2lD9TYg42TOwjkxHWdYah-5FF_5WEGEwtk", RequestHashes.template(INSTALL, """{"template":{}}""".toByteArray()))
    }

    private companion object {
        const val INSTALL = "3f6c1d2e-8a4b-4c5d-9e0f-1a2b3c4d5e6f"
    }
}
