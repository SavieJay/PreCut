package com.savie.precut.ui.pro

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.pro.ProCatalog
import com.savie.precut.model.pro.ProFeature
import com.savie.precut.model.pro.ProOffers
import com.savie.precut.pro.EntitlementRepository
import com.savie.precut.pro.PlayBillingGateway
import com.savie.precut.pro.PurchaseOutcome
import com.savie.precut.ui.navigation.PaywallRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.savie.precut.model.account.AuthResult
import com.savie.precut.model.pro.WebPlans
import com.savie.precut.pro.WebCheckout
import com.savie.precut.pro.WebPayment
import com.savie.precut.auth.AccountRepository
import com.savie.precut.model.account.AuthState
import com.savie.precut.model.account.obfuscatedAccountId

data class PaywallState(
    /** The Pro feature that opened the paywall, for its headline; null from Settings or onboarding. */
    val feature: ProFeature? = null,
    /** Null while Play is asked. */
    val offers: ProOffers? = null,
    val selected: String = ProCatalog.YEARLY,
    val working: Boolean = false,
    val failure: AiFailure? = null,
    /** Play's purchase screen failed or Play isn't available. */
    val playFailed: Boolean = false,
    val isPro: Boolean = false,
    /** Phase 31: Pro is sold through Paystack (the website APK), in naira. */
    val web: Boolean = false,
    /** A Paystack page to open in the browser, once. */
    val openUrl: String? = null,
    /** Back from Paystack without a completed payment. */
    val notPaid: Boolean = false,
)

/**
 * The paywall (Phase 25, design B1): yearly and monthly plans priced by Play, Continue opens
 * Play's purchase screen, Restore asks Play for this account's purchases. Pro comes only from the
 * server's check of the purchase.
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val billing: PlayBillingGateway,
    private val entitlements: EntitlementRepository,
    private val accounts: AccountRepository,
    private val web: WebCheckout,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<PaywallRoute>()
    private val _state = MutableStateFlow(PaywallState(feature = route.feature?.let { name -> ProFeature.entries.firstOrNull { it.name == name } }))
    val state: StateFlow<PaywallState> = _state.asStateFlow()

    init {
        if (web.available) {
            _state.update { it.copy(web = true, offers = WebPlans.offers(), selected = ProCatalog.YEARLY) }
        } else viewModelScope.launch {
            val offers = billing.offers()
            _state.update { it.copy(offers = offers, selected = if (offers.yearly != null) ProCatalog.YEARLY else ProCatalog.MONTHLY) }
        }
        viewModelScope.launch { entitlements.isPro.collect { pro -> _state.update { it.copy(isPro = pro) } } }
        viewModelScope.launch {
            billing.outcomes.collect { outcome ->
                // Purchased: the repository sends it to the server; Pro arrives through isPro.
                _state.update {
                    it.copy(
                        working = outcome is PurchaseOutcome.Purchased && !it.isPro,
                        playFailed = outcome is PurchaseOutcome.Failed,
                    )
                }
            }
        }
    }

    fun onSelect(basePlanId: String) = _state.update { it.copy(selected = basePlanId) }

    fun onContinue(activity: Activity) {
        val state = _state.value
        if (state.web) return startWeb(state.selected)
        val offer = state.offers?.let { if (state.selected == ProCatalog.YEARLY) it.yearly else it.monthly } ?: return
        viewModelScope.launch {
            _state.update { it.copy(failure = null, playFailed = false) }
            if (!billing.launch(activity, offer, (accounts.state.value as? AuthState.SignedIn)?.userId?.let(::obfuscatedAccountId))) _state.update { it.copy(playFailed = true) }
        }
    }

    /** The page opened (or couldn't be): don't open it again. */
    fun onUrlOpened() = _state.update { it.copy(openUrl = null) }

    /** Back in the app: a Paystack payment just made is checked (and Pro switches on). */
    fun onResumed() {
        if (_state.value.web && web.pending && !_state.value.working) confirmWeb(quietWhenNotPaid = false)
    }

    private fun startWeb(basePlanId: String) {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, failure = null, notPaid = false) }
            when (val result = web.start(basePlanId)) {
                is AuthResult.Ok -> _state.update { it.copy(working = false, openUrl = result.value) }
                is AuthResult.Failed -> _state.update { it.copy(working = false, failure = result.failure.asAiFailure()) }
            }
        }
    }

    private fun confirmWeb(quietWhenNotPaid: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(working = true, failure = null, notPaid = false) }
            when (val result = web.confirm()) {
                is AuthResult.Ok -> {
                    if (result.value == WebPayment.Paid) entitlements.refresh()
                    _state.update { it.copy(working = false, notPaid = result.value == WebPayment.NotPaid && !quietWhenNotPaid) }
                }
                is AuthResult.Failed -> _state.update { it.copy(working = false, failure = result.failure.asAiFailure()) }
            }
        }
    }

    fun onRestore() {
        if (_state.value.working) return
        // Website APK: "Check my payment" (a payment made, the app closed before coming back).
        if (_state.value.web) {
            if (web.pending) return confirmWeb(quietWhenNotPaid = false)
            viewModelScope.launch {
                _state.update { it.copy(working = true, failure = null) }
                val failure = entitlements.refresh()
                _state.update { it.copy(working = false, failure = failure) }
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(working = true, failure = null, playFailed = false) }
            val failure = entitlements.restore()
            _state.update { it.copy(working = false, failure = failure) }
        }
    }
}

/** Account-call failures as the paywall's messages. */
private fun com.savie.precut.model.account.AuthFailure.asAiFailure(): AiFailure = when (this) {
    com.savie.precut.model.account.AuthFailure.Offline -> AiFailure.Offline
    else -> AiFailure.Busy
}
