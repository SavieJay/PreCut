package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.Clip
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineOffsetToSourceMs
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Pure timeline layout math; no Compose so it can be unit-tested on the JVM. */
object TimelineGeometry {

    /** Zoom bounds and default, as screen dp per second of timeline. */
    const val MIN_DP_PER_SECOND = 6f
    const val DEFAULT_DP_PER_SECOND = 56f
    const val MAX_DP_PER_SECOND = 480f

    /** Ruler tick spacings to choose from, in ms. */
    private val TICK_INTERVALS_MS = longArrayOf(
        100, 200, 500, 1_000, 2_000, 5_000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000, 600_000,
    )

    fun msToPx(ms: Long, pxPerMs: Float): Float = ms * pxPerMs

    fun pxToMs(px: Float, pxPerMs: Float): Long = (px / pxPerMs).roundToLong()

    /**
     * Compose can't lay out content much wider than ~262k px, so long projects get a lower zoom
     * ceiling (and floor) that keeps the whole timeline under [MAX_CONTENT_PX].
     */
    const val MAX_CONTENT_PX = 120_000f

    fun clampZoom(dpPerSecond: Float, totalMs: Long, density: Float): Float {
        val seconds = (totalMs / 1000f).coerceAtLeast(1f)
        val ceiling = (MAX_CONTENT_PX / density / seconds).coerceAtMost(MAX_DP_PER_SECOND)
        val floor = MIN_DP_PER_SECOND.coerceAtMost(ceiling)
        return dpPerSecond.coerceIn(floor, ceiling)
    }

    /** Smallest "nice" tick interval whose ticks are at least [minSpacingPx] apart. */
    fun tickIntervalMs(pxPerMs: Float, minSpacingPx: Float): Long =
        TICK_INTERVALS_MS.firstOrNull { it * pxPerMs >= minSpacingPx } ?: TICK_INTERVALS_MS.last()

    /**
     * Source times for the thumbnail tiles across a clip drawn [clipWidthPx] wide, sampling the
     * middle of each tile. The last tile may be partially visible.
     */
    fun thumbnailSourceTimesMs(clip: Clip, clipWidthPx: Float, tileWidthPx: Float): List<Long> {
        if (clipWidthPx <= 0f || tileWidthPx <= 0f) return emptyList()
        val tiles = ceil(clipWidthPx / tileWidthPx).toInt()
        val duration = clip.timelineDurationMs()
        return List(tiles) { i ->
            val centerPx = ((i + 0.5f) * tileWidthPx).coerceAtMost(clipWidthPx)
            val offsetMs = (duration * (centerPx / clipWidthPx)).roundToLong()
            clip.timelineOffsetToSourceMs(offsetMs)
        }
    }

    /**
     * Shifts a time range by [deltaMs] keeping its length, clamped so it stays within
     * `0..totalMs`. A range longer than the timeline is pinned to 0.
     */
    fun moveRange(startMs: Long, endMs: Long, deltaMs: Long, totalMs: Long): Pair<Long, Long> {
        val length = endMs - startMs
        val start = (startMs + deltaMs).coerceIn(0L, (totalMs - length).coerceAtLeast(0L))
        return start to start + length
    }

    /** Target slot when a reorder tile is dropped with its centre at [centerX] in a row of [count] tiles. */
    fun reorderTargetIndex(centerX: Float, tileWidthPx: Float, count: Int): Int {
        if (count <= 0 || tileWidthPx <= 0f) return 0
        return (centerX / tileWidthPx).toInt().coerceIn(0, count - 1)
    }
}
