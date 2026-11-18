package com.savie.precut.model.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 31: the account's free trial, no free tastes with accounts, and the website's prices. */
class TrialRulesTest {

    private val day = 24L * 60 * 60 * 1_000
    private val now = 1_800_000_000_000L
    private fun trial(endsInMs: Long, pro: Boolean = true) =
        ProPlan(pro = pro, state = PlanState.ACTIVE, expiresAtMs = now + endsInMs, trial = true, source = PlanSource.TRIAL)

    @Test
    fun `days left round up, and only for a running trial`() {
        assertEquals(14, trialDaysLeft(trial(14 * day), now))
        assertEquals(3, trialDaysLeft(trial(2 * day + 1), now))
        assertEquals(1, trialDaysLeft(trial(60_000), now))
        assertNull(trialDaysLeft(trial(-1), now))
        assertNull(trialDaysLeft(trial(5 * day, pro = false), now))
        assertNull(trialDaysLeft(trial(5 * day).copy(source = PlanSource.PAYSTACK), now))
        assertNull(trialDaysLeft(null, now))
    }

    @Test
    fun `the notice shows in the last two days`() {
        assertFalse(trialEndingSoon(trial(3 * day), now))
        assertTrue(trialEndingSoon(trial(2 * day), now))
        assertTrue(trialEndingSoon(trial(1), now))
        assertFalse(trialEndingSoon(trial(-1), now))
    }

    @Test
    fun `with accounts there are no free tastes after the trial`() {
        assertEquals(ProAccess.Locked, proAccess(ProFeature.TRANSCRIPT_EDIT, isPro = false, usedThisMonth = 0, tastes = false))
        assertEquals(ProAccess.Unlocked, proAccess(ProFeature.TRANSCRIPT_EDIT, isPro = true, usedThisMonth = 0, tastes = false))
        assertEquals(ProAccess.Taste(3), proAccess(ProFeature.TRANSCRIPT_EDIT, isPro = false, usedThisMonth = 0, tastes = true))
    }

    @Test
    fun `the website plans are in naira, yearly cheaper than twelve months`() {
        val offers = WebPlans.offers()
        assertEquals("₦4,000", offers.monthly?.price)
        assertEquals("₦36,000", offers.yearly?.price)
        assertEquals("P1M", offers.monthly?.period)
        assertEquals("P1Y", offers.yearly?.period)
        assertEquals(0, offers.trialDays)
    }
}
