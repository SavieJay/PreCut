package com.savie.precut.model.pro

/** PreCut Pro's subscription in Play Console: one product, two base plans, a free trial offer. */
object ProCatalog {
    const val PRODUCT_ID = "precut_pro"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
}

/**
 * A plan the paywall can sell: a base plan, or an offer on it (the free trial), with Play's
 * [offerToken] and prices already formatted for the user's country.
 */
data class PlanOffer(
    val basePlanId: String,
    val offerToken: String,
    /** The recurring price, e.g. "₦12,000.00". */
    val price: String,
    val priceMicros: Long,
    /** ISO 8601 period of the recurring price, "P1M" or "P1Y". */
    val period: String,
    /** Days free before the first charge; 0 without a trial. */
    val freeTrialDays: Int,
)

/** The paywall's choice: the yearly and monthly plans, each its best offer (a trial when eligible). */
data class ProOffers(val yearly: PlanOffer?, val monthly: PlanOffer?) {
    val isEmpty: Boolean get() = yearly == null && monthly == null

    /** The longest free trial on offer, in days; 0 without one. */
    val trialDays: Int get() = maxOf(yearly?.freeTrialDays ?: 0, monthly?.freeTrialDays ?: 0)

    /**
     * How much cheaper yearly is than twelve months, in whole percent; null without both or when
     * it isn't cheaper.
     */
    val yearlySavingPercent: Int?
        get() {
            val year = yearly?.priceMicros ?: return null
            val month = monthly?.priceMicros?.takeIf { it > 0 } ?: return null
            val saving = 100 - (year * 100 / (month * 12))
            return saving.toInt().takeIf { it > 0 }
        }
}

/**
 * The offers Play returned for the product, sorted into yearly and monthly; for each, a free-trial
 * offer beats the plain base plan (Play only lists offers the user is eligible for).
 */
fun pickOffers(offers: List<PlanOffer>): ProOffers {
    fun best(basePlan: String) = offers.filter { it.basePlanId == basePlan }.maxByOrNull { it.freeTrialDays }
    return ProOffers(yearly = best(ProCatalog.YEARLY), monthly = best(ProCatalog.MONTHLY))
}

/** "P7D" → 7, "P1W" → 7, "P1M" → 30: a free phase's length in days (approximate for months). */
fun periodDays(iso: String): Int {
    val match = Regex("""P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?""").matchEntire(iso) ?: return 0
    val (y, m, w, d) = match.destructured
    return (y.toIntOrNull() ?: 0) * 365 + (m.toIntOrNull() ?: 0) * 30 + (w.toIntOrNull() ?: 0) * 7 + (d.toIntOrNull() ?: 0)
}
