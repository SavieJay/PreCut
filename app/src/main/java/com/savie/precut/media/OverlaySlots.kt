package com.savie.precut.media

/**
 * Media3 1.11.1's OverlayShaderProgram refuses more than 15 overlays in one OverlayEffect
 * ("does not support more than 15 SDR overlays in the same instance"), on every device.
 */
const val MAX_OVERLAYS_PER_EFFECT = 15

/**
 * Gives each time range (start until end, in list order) a slot, so ranges in the same slot never
 * overlap and can share one overlay: back-to-back captions all get slot 0. A range that overlaps
 * an earlier one gets a later slot than it, so it still draws on top.
 */
fun overlaySlots(ranges: List<LongRange>): List<Int> {
    val slots = ArrayList<Int>(ranges.size)
    ranges.forEachIndexed { i, range ->
        var slot = 0
        for (j in 0 until i) {
            if (ranges[j].overlaps(range)) slot = maxOf(slot, slots[j] + 1)
        }
        slots += slot
    }
    return slots
}

/** Splits [items] into runs of at most [size], in order (one OverlayEffect each). */
fun <T> overlayChunks(items: List<T>, size: Int = MAX_OVERLAYS_PER_EFFECT): List<List<T>> = items.chunked(size)

private fun LongRange.overlaps(other: LongRange): Boolean =
    !isEmpty() && !other.isEmpty() && first <= other.last && other.first <= last
