package com.savie.precut.model.growth

/** Successful exports before PreCut ever asks for a rating (Phase 28). */
const val EXPORTS_BEFORE_REVIEW = 2

/** At most one ask in this long (Google also limits how often the card shows). */
const val REVIEW_INTERVAL_MS = 45L * 24 * 60 * 60 * 1_000

/**
 * Whether to show Play's in-app review after an export that just succeeded: after a few good
 * exports, at most once every [REVIEW_INTERVAL_MS]. Never called after an error.
 */
fun shouldAskForReview(nowMs: Long, lastAskedMs: Long?, successfulExports: Int): Boolean =
    successfulExports >= EXPORTS_BEFORE_REVIEW && (lastAskedMs == null || nowMs - lastAskedMs >= REVIEW_INTERVAL_MS || nowMs < lastAskedMs)
