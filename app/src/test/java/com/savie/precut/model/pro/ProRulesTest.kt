package com.savie.precut.model.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProRulesTest {
    private val day = 24L * 60 * 60 * 1_000
    private val pro = ProPlan(pro = true, state = PlanState.ACTIVE, expiresAtMs = 10 * day, autoRenewing = true)

    @Test
    fun `pro is trusted offline until a little after it ends, and not from an old answer`() {
        assertTrue(isProAt(pro, checkedAtMs = 0L, nowMs = 5 * day))
        assertTrue(isProAt(pro, checkedAtMs = 0L, nowMs = 12 * day))
        assertFalse(isProAt(pro, checkedAtMs = 0L, nowMs = 14 * day))
        assertFalse(isProAt(pro.copy(expiresAtMs = null), checkedAtMs = 0L, nowMs = 31 * day))
        assertFalse(isProAt(pro.copy(pro = false), checkedAtMs = 0L, nowMs = 1L))
        assertFalse(isProAt(null, checkedAtMs = null, nowMs = 1L))
        // A clock set back doesn't take Pro away.
        assertTrue(isProAt(pro, checkedAtMs = 5 * day, nowMs = 1L))
    }

    @Test
    fun `payment problems and endings get a notice`() {
        assertEquals(PlanNotice.PAYMENT_ISSUE, planNotice(pro.copy(state = PlanState.GRACE)))
        assertEquals(PlanNotice.ON_HOLD, planNotice(pro.copy(pro = false, state = PlanState.ON_HOLD)))
        assertEquals(PlanNotice.PAUSED, planNotice(pro.copy(pro = false, state = PlanState.PAUSED)))
        assertEquals(PlanNotice.ENDING, planNotice(pro.copy(state = PlanState.CANCELED)))
        assertNull(planNotice(pro.copy(pro = false, state = PlanState.CANCELED)))
        assertNull(planNotice(pro))
    }

    @Test
    fun `the paywall picks each plan's trial when Play offers one`() {
        val yearly = PlanOffer(ProCatalog.YEARLY, "y", "$30", 30_000_000, "P1Y", 0)
        val trial = yearly.copy(offerToken = "y-trial", freeTrialDays = 7)
        val monthly = PlanOffer(ProCatalog.MONTHLY, "m", "$5", 5_000_000, "P1M", 0)
        val offers = pickOffers(listOf(yearly, monthly, trial, PlanOffer("weekly", "w", "$1", 1, "P1W", 0)))
        assertEquals(trial, offers.yearly)
        assertEquals(monthly, offers.monthly)
        assertEquals(50, offers.yearlySavingPercent)
        assertTrue(pickOffers(emptyList()).isEmpty)
        assertNull(ProOffers(yearly, null).yearlySavingPercent)
    }

    @Test
    fun `periods read as days`() {
        assertEquals(7, periodDays("P7D"))
        assertEquals(7, periodDays("P1W"))
        assertEquals(30, periodDays("P1M"))
        assertEquals(0, periodDays("soon"))
    }

    @Test
    fun `pro features are unlimited for Pro, a taste for free, then the paywall`() {
        assertEquals(ProAccess.Unlocked, proAccess(ProFeature.TRANSCRIPT_EDIT, isPro = true, usedThisMonth = 99))
        assertEquals(ProAccess.Taste(2), proAccess(ProFeature.TRANSCRIPT_EDIT, isPro = false, usedThisMonth = 1))
        assertEquals(ProAccess.Locked, proAccess(ProFeature.TRANSCRIPT_EDIT, isPro = false, usedThisMonth = 3))
        assertEquals(ProAccess.Locked, proAccess(ProFeature.BRAND_KIT, isPro = false, usedThisMonth = 0))
    }
}
