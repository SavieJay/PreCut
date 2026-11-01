package com.savie.precut.model.pro

import kotlinx.serialization.Serializable

/** A subscription's state as PreCut's server last heard it from Google Play (Phase 25). */
@Serializable
enum class PlanState { ACTIVE, GRACE, ON_HOLD, PAUSED, CANCELED, EXPIRED, PENDING }

/**
 * This install's PreCut Pro, as the server decided it from Google Play's records (never from the
 * app's own word). [state] and the rest describe the newest purchase, Pro or not, for messages.
 */
@Serializable
data class ProPlan(
    val pro: Boolean,
    val state: PlanState? = null,
    val basePlanId: String? = null,
    /** The end of what's paid for (renews then when [autoRenewing]). */
    val expiresAtMs: Long? = null,
    val autoRenewing: Boolean = false,
    val trial: Boolean = false,
)

/** How long a Pro answer may be trusted offline past its end (a renewal may not have reached the phone). */
const val OFFLINE_GRACE_MS = 3L * 24 * 60 * 60 * 1_000

/** How old a kept answer may get before Pro needs the server again. */
const val MAX_CACHE_AGE_MS = 30L * 24 * 60 * 60 * 1_000

/**
 * Whether this install is Pro at [nowMs] from the [plan] the server gave at [checkedAtMs]: Pro
 * while it said so, until a little after the paid period ends (renewals reach the phone on the next
 * check) and while the answer isn't too old. Offline with nothing kept: not Pro.
 */
fun isProAt(plan: ProPlan?, checkedAtMs: Long?, nowMs: Long): Boolean {
    if (plan == null || !plan.pro || checkedAtMs == null) return false
    if (nowMs < checkedAtMs) return true
    if (nowMs - checkedAtMs > MAX_CACHE_AGE_MS) return false
    return plan.expiresAtMs == null || nowMs < plan.expiresAtMs + OFFLINE_GRACE_MS
}

/** What Settings says about a plan that needs attention. */
enum class PlanNotice {
    /** Payment failed; still Pro while Google retries. */
    PAYMENT_ISSUE,

    /** Payment failed for too long: Pro is paused until the payment method is fixed. */
    ON_HOLD,

    /** Paused by the user in Google Play. */
    PAUSED,

    /** Won't renew; Pro until the end of the paid period. */
    ENDING,
}

fun planNotice(plan: ProPlan?): PlanNotice? = when (plan?.state) {
    PlanState.GRACE -> PlanNotice.PAYMENT_ISSUE
    PlanState.ON_HOLD -> PlanNotice.ON_HOLD
    PlanState.PAUSED -> PlanNotice.PAUSED
    PlanState.CANCELED -> if (plan.pro) PlanNotice.ENDING else null
    else -> null
}
