package com.savie.precut.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.auth.AccountRepository
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import com.savie.precut.model.account.AuthState
import com.savie.precut.model.account.CODE_LENGTH
import com.savie.precut.model.account.codeDigits
import com.savie.precut.model.account.looksLikeEmail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInStep { Email, Code }

data class SignInUi(
    val step: SignInStep = SignInStep.Email,
    val email: String = "",
    val code: String = "",
    val busy: Boolean = false,
    /** Null for none; [AuthFailure.Cancelled] is never shown. */
    val error: AuthFailure? = null,
    val resendInSec: Int = 0,
    val googleAvailable: Boolean = false,
    /** Signed out by the server, not by the user: the screen says why. */
    val expired: Boolean = false,
) {
    val canContinue: Boolean get() = !busy && looksLikeEmail(email)
    val canVerify: Boolean get() = !busy && code.length == CODE_LENGTH
}

/**
 * Sign-in (Phase 30): Google, or an email and the 6-digit code sent to it. Signing in changes
 * [AccountRepository.state], which the navigation follows; this screen only runs the steps.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    private val _ui = MutableStateFlow(
        SignInUi(
            email = (account.state.value as? AuthState.Expired)?.email ?: account.lastEmail.orEmpty(),
            googleAvailable = account.googleAvailable,
            expired = account.state.value is AuthState.Expired,
        ),
    )
    val ui: StateFlow<SignInUi> = _ui.asStateFlow()
    private var countdown: Job? = null

    fun onEmailChange(email: String) = _ui.update { it.copy(email = email.trim(), error = null) }

    fun onContinue() {
        if (!_ui.value.canContinue) {
            if (!looksLikeEmail(_ui.value.email)) _ui.update { it.copy(error = AuthFailure.BadEmail) }
            return
        }
        send()
    }

    fun onResend() {
        if (_ui.value.resendInSec == 0 && !_ui.value.busy) send()
    }

    fun onChangeEmail() {
        countdown?.cancel()
        _ui.update { it.copy(step = SignInStep.Email, code = "", error = null, resendInSec = 0) }
    }

    /** Six digits verify by themselves, so pasting the code is enough. */
    fun onCodeChange(text: String) {
        val code = codeDigits(text)
        _ui.update { it.copy(code = code, error = null) }
        if (code.length == CODE_LENGTH) onVerify()
    }

    fun onVerify() {
        val ui = _ui.value
        if (!ui.canVerify) return
        run { account.verifyCode(ui.email, ui.code) }
    }

    /** [activity] shows Google's sheet. */
    fun onGoogle(activity: Context) = run { account.signInWithGoogle(activity) }

    private fun send() {
        val email = _ui.value.email
        run {
            account.sendCode(email).also { result ->
                if (result is AuthResult.Ok) {
                    _ui.update { it.copy(step = SignInStep.Code, code = "") }
                    startCountdown()
                }
            }
        }
    }

    private fun run(block: suspend () -> AuthResult<Unit>) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = block()
            _ui.update {
                it.copy(
                    busy = false,
                    error = (result as? AuthResult.Failed)?.failure?.takeIf { f -> f != AuthFailure.Cancelled },
                    // A wrong code is cleared so the next one can be typed straight away.
                    code = if ((result as? AuthResult.Failed)?.failure == AuthFailure.BadCode) "" else it.code,
                )
            }
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in RESEND_AFTER_SEC downTo 0) {
                _ui.update { it.copy(resendInSec = left) }
                if (left > 0) delay(1_000)
            }
        }
    }

    private companion object {
        const val RESEND_AFTER_SEC = 60
    }
}
