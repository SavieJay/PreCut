package com.savie.precut.model.account

/** Whether someone is signed in (Phase 30). Pure Kotlin: the auth package fills it. */
sealed interface AuthState {
    /** The build has no account keys (docs/ACCOUNTS.md): no sign-in, the app works as before. */
    data object NotSetUp : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val userId: String, val email: String?) : AuthState

    /** The session ended on the server (signed out elsewhere, or deleted): sign in again. */
    data class Expired(val email: String?) : AuthState
}

/** Whether the app's screens need a sign-in first. */
val AuthState.needsSignIn: Boolean get() = this is AuthState.SignedOut || this is AuthState.Expired

/** A Supabase Auth session. [expiresAtSec] is the access token's end, in Unix seconds. */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtSec: Long,
    val userId: String,
    val email: String?,
) {
    /** Refreshed a minute early, so a request never leaves with a token about to end. */
    fun needsRefresh(nowSec: Long): Boolean = nowSec >= expiresAtSec - REFRESH_MARGIN_SEC

    private companion object {
        const val REFRESH_MARGIN_SEC = 60L
    }
}

/** Why signing in (or an account request) didn't work, as the screens say it. */
enum class AuthFailure {
    Offline,
    /** The code is wrong or has expired. */
    BadCode,
    /** Too many codes or attempts: wait a little. */
    TooMany,
    BadEmail,
    /** The session ended: sign in again. */
    SessionEnded,
    /** The person closed Google's sheet. */
    Cancelled,
    /** No Google account on the phone, or Google sign-in isn't available. */
    NoGoogleAccount,
    NotSetUp,
    Busy,
}

sealed interface AuthResult<out T> {
    data class Ok<T>(val value: T) : AuthResult<T>
    data class Failed(val failure: AuthFailure) : AuthResult<Nothing>
}

/**
 * Supabase Auth's refusals ([status], its `error_code`) as [AuthFailure]s. Refresh-token codes
 * mean the session is over; unknown ones are treated as a busy server.
 */
fun authFailureFor(status: Int, errorCode: String?): AuthFailure = when (errorCode) {
    "otp_expired", "otp_disabled" -> AuthFailure.BadCode
    "over_email_send_rate_limit", "over_request_rate_limit" -> AuthFailure.TooMany
    "email_address_invalid", "email_address_not_authorized" -> AuthFailure.BadEmail
    "refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired",
    "user_not_found", "user_banned", "bad_jwt",
    -> AuthFailure.SessionEnded
    "signup_disabled", "email_provider_disabled", "provider_disabled" -> AuthFailure.NotSetUp
    else -> when (status) {
        429 -> AuthFailure.TooMany
        401, 403 -> AuthFailure.SessionEnded
        else -> AuthFailure.Busy
    }
}

/**
 * The account as Google Play's `obfuscatedAccountId` (Phase 30): the SHA-256 of the user id in hex,
 * 64 characters, as Play asks (one-way, never the id or an email).
 */
fun obfuscatedAccountId(userId: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(userId.encodeToByteArray()).joinToString("") { "%02x".format(it) }

/** A plausible email address: something, @, a domain with a dot. The server has the last word. */
fun looksLikeEmail(text: String): Boolean = EMAIL.matches(text.trim())

/** The 6 digits of an email code, from whatever was typed or pasted. */
fun codeDigits(text: String): String = text.filter(Char::isDigit).take(CODE_LENGTH)

const val CODE_LENGTH = 6

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
