package com.savie.precut.pro

import com.android.billingclient.api.PendingPurchasesParams
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlayBillingGatewayTest {

    /** Play Billing accepts PreCut's pending-purchase settings; its builder throws when it doesn't. */
    @Test
    fun pendingPurchases_areAcceptedByPlayBilling() {
        assertNotNull(PlayBillingGateway.pendingPurchases())
    }

    /** The 1.1.0 test build's settings: Play Billing refuses them, which crashed every app start. */
    @Test(expected = IllegalArgumentException::class)
    fun prepaidPlansAlone_areRefused() {
        PendingPurchasesParams.newBuilder().enablePrepaidPlans().build()
    }
}
