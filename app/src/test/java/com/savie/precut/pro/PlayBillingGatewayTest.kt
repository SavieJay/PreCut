package com.savie.precut.pro

import org.junit.Assert.assertTrue
import org.junit.Test

class PlayBillingGatewayTest {

    /** Play Billing refuses prepaid plans without one-time products, which crashed every app start (1.1.0 test build). */
    @Test
    fun pendingPurchases_areAcceptedByPlayBilling() {
        val params = PlayBillingGateway.pendingPurchases()
        assertTrue(params.isEnabledForOneTimeProducts)
        assertTrue(params.isEnabledForPrepaidPlans)
    }
}
