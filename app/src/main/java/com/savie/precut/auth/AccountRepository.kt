package com.savie.precut.auth

import android.content.Context
import com.savie.precut.ai.cloud.InstallStore
import com.savie.precut.di.ApplicationScope
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import com.savie.precut.model.account.AuthState
import com.savie.precut.model.account.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in user and their access token, for the cloud routes (sync uses only this). */
interface AccessTokens {
    /** The signed-in user's id; null when no one is. */
    val userId: String?

    /** Runs [call] with a current token; a token the server refuses ends the session. */
    suspend fun <T> withToken(call: suspend (String) -> AuthResult<T>): AuthResult<T>
}

/**
 * Who is signed in (Phase 30), and their session: kept between launches ([SessionStore]),
 * refreshed a minute before it ends, and given up only when the server says it's over (then
 * [state] is [AuthState.Expired]; projects on the phone stay). Offline, a stored session still
 * counts as signed in.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val config: AuthConfig,
    private val auth: AuthClient,
    private val google: GoogleSignIn,
    private val api: AccountApi,
    private val store: SessionStore,
    private val installs: InstallStore,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : AccessTokens {
    private val mutex = Mutex()
    private var session: Session? = if (config.isSetUp) store.load() else null

    private val _state = MutableStateFlow(
        when {
            !config.isSetUp -> AuthState.NotSetUp
            else -> session?.let { AuthState.SignedIn(it.userId, it.email) } ?: AuthState.SignedOut
        },
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** The last account's email, offered on the sign-in screen. */
    val lastEmail: String? get() = store.lastEmail

    val googleAvailable: Boolean get() = config.googleSetUp

    suspend fun sendCode(email: String): AuthResult<Unit> = auth.sendCode(email.trim())

    suspend fun verifyCode(email: String, code: String): AuthResult<Unit> =
        signedIn(auth.verifyCode(email.trim(), code))

    suspend fun signInWithGoogle(activity: Context): AuthResult<Unit> =
        when (val token = google.idToken(activity)) {
            is AuthResult.Ok -> signedIn(auth.signInWithGoogle(token.value.idToken, token.value.rawNonce))
            is AuthResult.Failed -> token
        }

    /** A current access token, refreshed when needed; [AuthFailure.SessionEnded] signs out. */
    suspend fun accessToken(): AuthResult<String> = mutex.withLock {
        val current = session ?: return AuthResult.Failed(AuthFailure.SessionEnded)
        if (!current.needsRefresh(clock.instant().epochSecond)) return AuthResult.Ok(current.accessToken)
        when (val refreshed = auth.refresh(current.refreshToken)) {
            is AuthResult.Ok -> {
                keep(refreshed.value)
                AuthResult.Ok(refreshed.value.accessToken)
            }
            is AuthResult.Failed -> {
                if (refreshed.failure == AuthFailure.SessionEnded) end(expired = true)
                refreshed
            }
        }
    }

    override val userId: String? get() = (_state.value as? AuthState.SignedIn)?.userId

    override suspend fun <T> withToken(call: suspend (String) -> AuthResult<T>): AuthResult<T> {
        val token = when (val result = accessToken()) {
            is AuthResult.Ok -> result.value
            is AuthResult.Failed -> return result
        }
        val result = call(token)
        if (result is AuthResult.Failed && result.failure == AuthFailure.SessionEnded) {
            mutex.withLock { end(expired = true) }
        }
        return result
    }

    suspend fun account(): AuthResult<CloudAccount> = withToken(api::account)

    /** Signs out here and on the server (best effort: offline, the phone forgets it anyway). */
    suspend fun signOut() {
        val token = mutex.withLock { session?.accessToken.also { end(expired = false) } }
        if (token != null) scope.launch { auth.signOut(token) }
    }

    /** Deletes the account and everything of it on the server; the phone then signs out. */
    suspend fun deleteAccount(): AuthResult<Unit> {
        val result = withToken(api::delete)
        if (result is AuthResult.Ok) mutex.withLock { end(expired = false) }
        return result
    }

    /** Ties this install (and its purchases) to the account; tried again at each launch until done. */
    fun linkInstall() {
        val installToken = installs.token ?: return
        scope.launch { withToken { api.link(it, installToken) } }
    }

    private suspend fun signedIn(result: AuthResult<Session>): AuthResult<Unit> = when (result) {
        is AuthResult.Ok -> {
            mutex.withLock { keep(result.value) }
            linkInstall()
            AuthResult.Ok(Unit)
        }
        is AuthResult.Failed -> result
    }

    private fun keep(new: Session) {
        session = new
        store.save(new)
        _state.value = AuthState.SignedIn(new.userId, new.email)
    }

    private fun end(expired: Boolean) {
        val email = session?.email ?: store.lastEmail
        session = null
        store.save(null)
        _state.value = if (expired) AuthState.Expired(email) else AuthState.SignedOut
    }
}
