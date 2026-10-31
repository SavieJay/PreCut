package com.savie.precut.model.ai

/** A stretch of the timeline's sound sent in one upload, [startMs] until [endMs]. */
data class SpeechPart(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Sound longer than one upload allows is cut into parts (Phase 21A). Each cut is at the quietest
 * moment in the [SEARCH_MS] before the limit, so a word is rarely cut in half, and no part is
 * longer than the limit.
 */
object SpeechParts {
    /** How far back from the limit a cut may move to find a quiet moment. */
    const val SEARCH_MS = 30_000L

    /**
     * Parts covering 0 until [durationMs], each at most [maxPartMs]. [levels] are the sound's peak
     * levels (0..1), one per [windowMs] from the start; without them, cuts fall at the limit.
     */
    fun plan(durationMs: Long, maxPartMs: Long, levels: FloatArray = FloatArray(0), windowMs: Long = 50L, searchMs: Long = SEARCH_MS): List<SpeechPart> {
        require(maxPartMs > 0 && windowMs > 0)
        if (durationMs <= 0L) return emptyList()
        val parts = mutableListOf<SpeechPart>()
        var start = 0L
        while (durationMs - start > maxPartMs) {
            val limit = start + maxPartMs
            val cut = quietestBefore(limit, (limit - searchMs).coerceAtLeast(start + maxPartMs / 2), levels, windowMs)
            parts += SpeechPart(start, cut)
            start = cut
        }
        parts += SpeechPart(start, durationMs)
        return parts
    }

    /** What the server charges for [parts]: each part's length in whole seconds, rounded up. */
    fun chargedSeconds(parts: List<SpeechPart>): Int = parts.sumOf { ((it.durationMs + 999) / 1_000).toInt() }

    /** The leading [parts] whose charges add up to [maxSeconds] or less. */
    fun affordable(parts: List<SpeechPart>, maxSeconds: Int): List<SpeechPart> {
        var total = 0
        return parts.takeWhile { part ->
            total += chargedSeconds(listOf(part))
            total <= maxSeconds
        }
    }

    /**
     * The middle of the quietest window that ends by [limit] and starts at [earliest] or later (the
     * latest one on a tie); [limit] itself when no level covers that stretch.
     */
    private fun quietestBefore(limit: Long, earliest: Long, levels: FloatArray, windowMs: Long): Long {
        val first = ((earliest + windowMs - 1) / windowMs).toInt()
        val last = (limit / windowMs - 1).toInt().coerceAtMost(levels.size - 1)
        if (first > last) return limit
        var best = last
        for (i in last downTo first) {
            if (levels[i] < levels[best]) best = i
        }
        return best * windowMs + windowMs / 2
    }
}
