package com.savie.precut.pro

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.savie.precut.model.pro.PlanOffer
import com.savie.precut.model.pro.ProCatalog
import com.savie.precut.model.pro.ProOffers
import com.savie.precut.model.pro.periodDays
import com.savie.precut.model.pro.pickOffers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What came back from Play's purchase screen. */
sealed interface PurchaseOutcome {
    data class Purchased(val purchases: List<Purchase>) : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val code: Int) : PurchaseOutcome
}

/**
 * Google Play Billing (Phase 25), the only way PreCut sells anything in the Play build: the Pro
 * product's offers, Play's purchase screen, and the purchases this Google account owns (Restore).
 * Purchases are never trusted here: [EntitlementRepository] sends them to PreCut's server.
 */
@Singleton
class PlayBillingGateway @Inject constructor(@ApplicationContext context: Context) {
    private val _outcomes = MutableSharedFlow<PurchaseOutcome>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Results of purchase screens, and purchases Play delivers later (pending ones that went through). */
    val outcomes: SharedFlow<PurchaseOutcome> = _outcomes.asSharedFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            _outcomes.tryEmit(
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK -> PurchaseOutcome.Purchased(purchases.orEmpty())
                    BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
                    else -> PurchaseOutcome.Failed(result.responseCode)
                },
            )
        }
        .enablePendingPurchases(pendingPurchases())
        .enableAutoServiceReconnection()
        .build()

    private val connecting = Mutex()
    private var details: ProductDetails? = null

    /** The paywall's plans, priced for this user; empty when Play can't say (no Play, offline, not set up). */
    suspend fun offers(): ProOffers {
        val product = productDetails() ?: return ProOffers(null, null)
        val offers = product.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
            val phases = offer.pricingPhases.pricingPhaseList
            val recurring = phases.lastOrNull() ?: return@mapNotNull null
            val free = phases.firstOrNull { it.priceAmountMicros == 0L }
            PlanOffer(
                basePlanId = offer.basePlanId,
                offerToken = offer.offerToken,
                price = recurring.formattedPrice,
                priceMicros = recurring.priceAmountMicros,
                period = recurring.billingPeriod,
                freeTrialDays = free?.let { periodDays(it.billingPeriod) * it.billingCycleCount.coerceAtLeast(1) } ?: 0,
            )
        }
        return pickOffers(offers)
    }

    /** Opens Play's purchase screen for [offer]; the result arrives in [outcomes]. False if Play couldn't. */
    suspend fun launch(activity: Activity, offer: PlanOffer): Boolean {
        val product = productDetails() ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer.offerToken).build()),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** The Pro subscriptions this Google account owns (for Restore, and checks on start); null if Play can't say. */
    suspend fun ownedPurchases(): List<Purchase>? {
        if (!connect()) return null
        return suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
                continuation.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.filter(::isPro) else null)
            }
        }
    }

    private fun isPro(purchase: Purchase): Boolean = ProCatalog.PRODUCT_ID in purchase.products

    private suspend fun productDetails(): ProductDetails? {
        details?.let { return it }
        if (!connect()) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(ProCatalog.PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()),
            )
            .build()
        return suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { result, found ->
                val product = found.productDetailsList.firstOrNull().takeIf { result.responseCode == BillingClient.BillingResponseCode.OK }
                if (product == null) Log.w(TAG, "No product details: ${result.responseCode} ${result.debugMessage}")
                details = product
                continuation.resume(product)
            }
        }
    }

    private suspend fun connect(): Boolean = connecting.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    internal companion object {
        private const val TAG = "PlayBilling"

        /**
         * Pending purchases (a payment still going through), as Play Billing 7+ requires: it refuses
         * prepaid plans without one-time products ("Pending purchases for one-time products must be
         * supported.") and throws while the client is built, which crashed the app at every start.
         * PreCut sells only subscriptions, so pending one-time products never arrive.
         */
        fun pendingPurchases(): PendingPurchasesParams =
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build()
    }
}
