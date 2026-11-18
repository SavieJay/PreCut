package com.savie.precut.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.pro.EntitlementRepository
import com.savie.precut.pro.KnownPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.savie.precut.model.account.AuthResult
import com.savie.precut.pro.WebCheckout
import java.time.Clock

data class ProSectionState(
    val known: KnownPlan = KnownPlan(null, null),
    val isPro: Boolean = false,
    val restoring: Boolean = false,
    val restored: Boolean = false,
    val failure: AiFailure? = null,
    /** Phase 31: Pro is sold through Paystack in this build. */
    val web: Boolean = false,
    val cancelling: Boolean = false,
    val cancelFailed: Boolean = false,
    val nowMs: Long = 0L,
)

/** Settings → PreCut Pro: the server's plan, refreshed when shown; Restore purchases. */
@HiltViewModel
class ProSectionViewModel @Inject constructor(
    private val entitlements: EntitlementRepository,
    private val web: WebCheckout,
    private val clock: Clock,
) : ViewModel() {
    private val restore = MutableStateFlow(ProSectionState(web = web.available))

    val state: StateFlow<ProSectionState> = combine(entitlements.known, entitlements.isPro, restore) { known, pro, local ->
        local.copy(known = known, isPro = pro, nowMs = clock.millis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProSectionState())

    fun onShown() {
        viewModelScope.launch { entitlements.refresh() }
    }

    /** Stops a Paystack subscription renewing (Pro lasts the period). */
    fun onCancel() {
        if (restore.value.cancelling) return
        viewModelScope.launch {
            restore.value = restore.value.copy(cancelling = true, cancelFailed = false)
            val result = web.cancel()
            if (result is AuthResult.Ok) entitlements.refresh()
            restore.value = restore.value.copy(cancelling = false, cancelFailed = result is AuthResult.Failed)
        }
    }

    fun onRestore() {
        if (restore.value.restoring) return
        viewModelScope.launch {
            restore.value = restore.value.copy(restoring = true, failure = null, restored = false)
            // Website APK: "Check my payment" — a Paystack payment the app didn't see come back.
            if (restore.value.web) web.confirm()
            val failure = if (restore.value.web) entitlements.refresh() else entitlements.restore()
            restore.value = restore.value.copy(restoring = false, failure = failure, restored = failure == null)
        }
    }
}
