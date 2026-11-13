package com.savie.precut.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** A Google ID token for Supabase, and the nonce it was made with (Supabase checks its hash). */
data class GoogleToken(val idToken: String, val rawNonce: String)

/**
 * "Continue with Google" (Phase 30): Credential Manager's Sign in with Google sheet, for the web
 * client ID Supabase knows. Google gets the SHA-256 of a fresh nonce, Supabase the nonce itself.
 */
@Singleton
class GoogleSignIn @Inject constructor(private val config: AuthConfig) {

    /** [activity] must be an Activity: Credential Manager shows its sheet over it. */
    suspend fun idToken(activity: Context): AuthResult<GoogleToken> {
        if (!config.googleSetUp) return AuthResult.Failed(AuthFailure.NotSetUp)
        val rawNonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes).toHex()
        val option = GetSignInWithGoogleOption.Builder(config.googleWebClientId)
            .setNonce(sha256Hex(rawNonce))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                AuthResult.Ok(GoogleToken(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce))
            } else {
                AuthResult.Failed(AuthFailure.Busy)
            }
        } catch (_: GetCredentialCancellationException) {
            AuthResult.Failed(AuthFailure.Cancelled)
        } catch (_: NoCredentialException) {
            AuthResult.Failed(AuthFailure.NoGoogleAccount)
        } catch (_: GetCredentialException) {
            AuthResult.Failed(AuthFailure.Busy)
        } catch (_: GoogleIdTokenParsingException) {
            AuthResult.Failed(AuthFailure.Busy)
        }
    }

    private companion object {
        const val NONCE_BYTES = 32

        fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).toHex()

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
