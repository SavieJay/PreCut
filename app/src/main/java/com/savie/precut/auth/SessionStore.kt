package com.savie.precut.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.savie.precut.ai.cloud.cloudJson
import com.savie.precut.model.account.Session
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the signed-in session between launches. */
interface SessionStore {
    fun load(): Session?
    fun save(session: Session?)
    /** The last account's email, kept after sign-out so the sign-in screen can offer it. */
    var lastEmail: String?
}

/**
 * In private preferences `session`, which the backup rules leave out (a restored phone signs in
 * again), encrypted with an AES-GCM key that never leaves the Android Keystore. A key that can't
 * be used any more (the Keystore was reset) just means signing in again.
 */
@Singleton
class KeystoreSessionStore @Inject constructor(@ApplicationContext context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    override fun load(): Session? {
        val sealed = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val bytes = Base64.decode(sealed, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            val text = cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
            cloudJson.decodeFromString<StoredSession>(text).toSession()
        } catch (_: GeneralSecurityException) {
            prefs.edit { remove(KEY_SESSION) }
            null
        } catch (_: IllegalArgumentException) {
            prefs.edit { remove(KEY_SESSION) }
            null
        } catch (_: SerializationException) {
            prefs.edit { remove(KEY_SESSION) }
            null
        }
    }

    @Synchronized
    override fun save(session: Session?) {
        if (session == null) {
            prefs.edit { remove(KEY_SESSION) }
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(cloudJson.encodeToString(StoredSession.serializer(), StoredSession.of(session)).encodeToByteArray())
        prefs.edit {
            putString(KEY_SESSION, Base64.encodeToString(sealed, Base64.NO_WRAP))
            session.email?.let { putString(KEY_EMAIL, it) }
        }
    }

    override var lastEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_EMAIL) else putString(KEY_EMAIL, value) }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "session"
        const val KEY_SESSION = "session"
        const val KEY_EMAIL = "last_email"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "precut_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
    }
}

@Serializable
private data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtSec: Long,
    val userId: String,
    val email: String? = null,
) {
    fun toSession() = Session(accessToken, refreshToken, expiresAtSec, userId, email)

    companion object {
        fun of(s: Session) = StoredSession(s.accessToken, s.refreshToken, s.expiresAtSec, s.userId, s.email)
    }
}
