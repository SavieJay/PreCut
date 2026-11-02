package com.savie.precut.pro

import javax.inject.Inject

/**
 * How many free days Google Play would give this user before Pro's first charge: 0 when there's
 * no trial for them (a copy not installed from Play, offline, Play not set up, or a trial already
 * used; Play only lists offers the user is eligible for). Onboarding offers the trial only then.
 */
fun interface TrialOffers {
    suspend fun trialDays(): Int
}

class PlayTrialOffers @Inject constructor(private val billing: PlayBillingGateway) : TrialOffers {
    override suspend fun trialDays(): Int = billing.offers().trialDays
}
