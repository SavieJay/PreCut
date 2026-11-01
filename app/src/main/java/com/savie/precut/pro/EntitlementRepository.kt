package com.savie.precut.pro

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.android.billingclient.api.Purchase
import com.savie.precut.ai.cloud.CloudClient
import com.savie.precut.di.ApplicationScope
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.ai.CloudStatus
import com.savie.precut.model.pro.ProPlan
import com.savie.precut.model.pro.isProAt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** The last plan the server gave, and when. */
data class KnownPlan(val plan: ProPlan?, val checkedAtMs: Long?, val status: CloudStatus? = null)

/**
 * PreCut Pro (Phase 25): [isPro] comes only from PreCut's server, which checks every purchase with
 * Google Play. The last answer is kept for offline use with an expiry ([isProAt]); new purchases,
 * Restore and app starts send this Google account's purchases to the server.
 */
@Singleton
class EntitlementRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val cloud: CloudClient,
    private val billing: PlayBillingGateway,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _known = MutableStateFlow(KnownPlan(readPlan(), prefs.getLong(KEY_CHECKED, -1L).takeIf { it >= 0 }))

    /** The plan as last heard (with the full status when it came with one). */
    val known: StateFlow<KnownPlan> = _known.asStateFlow()

    val isPro: StateFlow<Boolean> = _known.map { isProAt(it.plan, it.checkedAtMs, clock.millis()) }
        .stateIn(scope, SharingStarted.Eagerly, isProAt(_known.value.plan, _known.value.checkedAtMs, clock.millis()))

    init {
        // Purchases finished on Play's screen (or pending ones that went through) go to the server.
        scope.launch {
            billing.outcomes.collect { outcome ->
                if (outcome is PurchaseOutcome.Purchased) send(outcome.purchases)
            }
        }
    }

    /**
     * On app start: asks the server again when the last answer is over [STALE_MS] old, and sends
     * purchases Play knows but the server hasn't confirmed (a purchase cut short, a reinstall).
     * Nothing is sent for someone who never bought Pro.
     */
    suspend fun refreshIfStale() {
        val checked = _known.value.checkedAtMs
        if (checked != null && clock.millis() - checked in 0 until STALE_MS) return
        refresh()
        if (!isProAt(_known.value.plan, _known.value.checkedAtMs, clock.millis())) {
            billing.ownedPurchases()?.takeIf { it.isNotEmpty() }?.let { send(it) }
        }
    }

    /** Asks the server again (Settings, the paywall, app start). */
    suspend fun refresh(): AiFailure? = when (val result = cloud.status(forPro = true)) {
        is AiResult.Ok -> {
            result.value?.let(::keep)
            null
        }
        is AiResult.Failed -> result.failure
    }

    /**
     * Restore purchases: everything this Google account owns goes to the server, which links what
     * it finds valid to this install. Null when it worked (Pro or not: [isPro] says).
     */
    suspend fun restore(): AiFailure? {
        val owned = billing.ownedPurchases() ?: return AiFailure.NoPlayServices
        if (owned.isEmpty()) return refresh()
        return send(owned)
    }

    /** Sends purchased (not pending) subscriptions; the server acknowledges them. */
    private suspend fun send(purchases: List<Purchase>): AiFailure? {
        var failure: AiFailure? = null
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
            when (val result = cloud.purchase(purchase.purchaseToken)) {
                is AiResult.Ok -> keep(result.value)
                is AiResult.Failed -> {
                    Log.w(TAG, "A purchase wasn't confirmed: ${result.failure}")
                    failure = result.failure
                }
            }
        }
        return failure
    }

    private fun keep(status: CloudStatus) {
        val now = clock.millis()
        val plan = status.plan ?: ProPlan(pro = false)
        prefs.edit {
            putString(KEY_PLAN, json.encodeToString(ProPlan.serializer(), plan))
            putLong(KEY_CHECKED, now)
        }
        _known.value = KnownPlan(plan, now, status)
    }

    private fun readPlan(): ProPlan? = prefs.getString(KEY_PLAN, null)?.let {
        try {
            json.decodeFromString(ProPlan.serializer(), it)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val TAG = "Entitlements"
        const val PREFS = "pro"
        const val KEY_PLAN = "plan"
        const val KEY_CHECKED = "checkedAtMs"
        const val STALE_MS = 6L * 60 * 60 * 1_000
    }
}
