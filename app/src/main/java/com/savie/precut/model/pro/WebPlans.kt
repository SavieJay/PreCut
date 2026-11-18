package com.savie.precut.model.pro

/**
 * PreCut Pro on the website APK (Phase 31): paid with Paystack, in naira. The server's Paystack
 * plans charge these amounts (Paystack may add its fee at checkout); the paywall shows them with
 * the same plan cards as Google Play's offers.
 */
object WebPlans {
    const val MONTHLY_NAIRA = 4_000
    const val YEARLY_NAIRA = 36_000

    /** The two plans as paywall offers ([PlanOffer.offerToken] says which Paystack plan). */
    fun offers(): ProOffers = ProOffers(
        yearly = PlanOffer(ProCatalog.YEARLY, ProCatalog.YEARLY, naira(YEARLY_NAIRA), YEARLY_NAIRA * MICROS, "P1Y", freeTrialDays = 0),
        monthly = PlanOffer(ProCatalog.MONTHLY, ProCatalog.MONTHLY, naira(MONTHLY_NAIRA), MONTHLY_NAIRA * MICROS, "P1M", freeTrialDays = 0),
    )

    /** "₦36,000". */
    fun naira(amount: Int): String = "₦" + "%,d".format(java.util.Locale.ROOT, amount)

    private const val MICROS = 1_000_000L
}
