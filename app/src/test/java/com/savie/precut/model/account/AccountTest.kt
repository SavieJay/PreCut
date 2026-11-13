package com.savie.precut.model.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 30: the account rules that don't need Android. */
class AccountTest {

    @Test
    fun `a session refreshes a minute before it ends`() {
        val session = Session("a", "r", expiresAtSec = 1_000, userId = "u", email = null)
        assertFalse(session.needsRefresh(939))
        assertTrue(session.needsRefresh(940))
        assertTrue(session.needsRefresh(2_000))
    }

    @Test
    fun `Supabase Auth's refusals read as what the screen says`() {
        assertEquals(AuthFailure.BadCode, authFailureFor(403, "otp_expired"))
        assertEquals(AuthFailure.TooMany, authFailureFor(429, "over_email_send_rate_limit"))
        assertEquals(AuthFailure.TooMany, authFailureFor(429, null))
        assertEquals(AuthFailure.BadEmail, authFailureFor(400, "email_address_invalid"))
        assertEquals(AuthFailure.SessionEnded, authFailureFor(400, "refresh_token_not_found"))
        assertEquals(AuthFailure.SessionEnded, authFailureFor(400, "refresh_token_already_used"))
        assertEquals(AuthFailure.SessionEnded, authFailureFor(403, "user_not_found"))
        assertEquals(AuthFailure.NotSetUp, authFailureFor(422, "provider_disabled"))
        assertEquals(AuthFailure.Busy, authFailureFor(500, null))
        assertEquals(AuthFailure.Busy, authFailureFor(400, "something_new"))
    }

    @Test
    fun `only signed-out states need the sign-in screen`() {
        assertTrue(AuthState.SignedOut.needsSignIn)
        assertTrue(AuthState.Expired("a@b.co").needsSignIn)
        assertFalse(AuthState.SignedIn("u", null).needsSignIn)
        assertFalse(AuthState.NotSetUp.needsSignIn)
    }

    @Test
    fun `emails and codes are read forgivingly`() {
        assertTrue(looksLikeEmail(" ana@example.com "))
        assertFalse(looksLikeEmail("ana@example"))
        assertFalse(looksLikeEmail("ana example.com"))
        assertFalse(looksLikeEmail(""))
        assertEquals("123456", codeDigits("123 456"))
        assertEquals("123456", codeDigits("Your code: 1234567"))
        assertEquals("12", codeDigits("12ab"))
    }

    @Test
    fun `Play gets a one-way 64-character account id`() {
        val id = obfuscatedAccountId("11111111-1111-4111-8111-111111111111")
        assertEquals(64, id.length)
        assertTrue(id.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(id, obfuscatedAccountId("11111111-1111-4111-8111-111111111111"))
        assertFalse(id == obfuscatedAccountId("22222222-2222-4222-8222-222222222222"))
    }
}
