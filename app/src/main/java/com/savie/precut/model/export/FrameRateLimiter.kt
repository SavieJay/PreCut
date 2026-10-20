package com.savie.precut.model.export

/**
 * Lowers a stream's frame rate to at most [frameRate] without holding frames back: each frame
 * stands for the time from halfway to its predecessor to halfway past itself, and is kept when
 * that span reaches a point of the [frameRate] grid no kept frame has reached yet. A stream already
 * at (or under) the rate keeps every frame, timing jitter included; 60 → 30 keeps every other
 * frame and 30 → 24 four in five. Timestamps going backwards start over.
 */
class FrameRateLimiter(private val frameRate: Int) {

    init {
        require(frameRate > 0) { "frameRate must be positive" }
    }

    private var previousUs = NONE
    /** The first grid point no kept frame has reached yet. */
    private var nextGridUs = NONE

    /** Whether the frame at [presentationTimeUs] is kept; call once per frame, in order. */
    fun keep(presentationTimeUs: Long): Boolean {
        val previous = previousUs
        previousUs = presentationTimeUs
        if (previous == NONE || presentationTimeUs <= previous) {
            nextGridUs = gridAfter(presentationTimeUs)
            return true
        }
        val reachUs = presentationTimeUs + (presentationTimeUs - previous) / 2
        if (reachUs <= nextGridUs) return false
        nextGridUs = gridAfter(reachUs)
        return true
    }

    fun reset() {
        previousUs = NONE
        nextGridUs = NONE
    }

    /** The first grid point after [timeUs]. */
    private fun gridAfter(timeUs: Long): Long {
        val index = Math.floorDiv(timeUs * frameRate, MICROS_PER_SECOND) + 1
        return Math.floorDiv(index * MICROS_PER_SECOND + frameRate - 1, frameRate.toLong())
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
