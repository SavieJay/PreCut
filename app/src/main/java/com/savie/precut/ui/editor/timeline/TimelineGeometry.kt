package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.Clip
import com.savie.precut.model.averageSpeed
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.edit.MIN_CLIP_DURATION_MS
import com.savie.precut.model.timelineOffsetToSourceMs
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Pure timeline layout math; no Compose so it can be unit-tested on the JVM. */
object TimelineGeometry {

    /** Zoom bounds and default, as screen dp per second of timeline. */
    const val MIN_DP_PER_SECOND = 6f
    const val DEFAULT_DP_PER_SECOND = 32f
    const val MAX_DP_PER_SECOND = 1_500f

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

    /**
     * Where a text tile ends up after a body move ([moveDeltaMs], keeps the length) and handle
     * drags ([startDeltaMs], [endDeltaMs]). Stays within `0..totalMs` and at least
     * [MIN_CLIP_DURATION_MS] long (when the timeline allows).
     */
    fun dragTextRange(
        startMs: Long,
        endMs: Long,
        moveDeltaMs: Long,
        startDeltaMs: Long,
        endDeltaMs: Long,
        totalMs: Long,
    ): Pair<Long, Long> {
        val (movedStart, movedEnd) = moveRange(startMs, endMs, moveDeltaMs, totalMs)
        val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(totalMs.coerceAtLeast(0L))
        val start = (movedStart + startDeltaMs).coerceIn(0L, (movedEnd - minLength).coerceAtLeast(0L))
        val end = (movedEnd + endDeltaMs).coerceIn(start + minLength, maxOf(totalMs, start + minLength))
        return start to end
    }

    /** Audio timing after a drag: where it starts on the timeline and which source range plays. */
    data class AudioTiming(val timelineStartMs: Long, val trimStartMs: Long, val trimEndMs: Long)

    /**
     * Applies an audio bar drag; deltas are timeline ms, converted to source ms with [speed]. A
     * body move shifts the start (not before 0). The start handle trims the source *and* moves the
     * start by the same timeline amount, so the remaining audio stays put. The end handle trims the
     * end. Keeps at least [MIN_CLIP_DURATION_MS] of source.
     */
    fun dragAudio(
        timing: AudioTiming,
        sourceDurationMs: Long,
        speed: Float,
        moveDeltaMs: Long,
        startDeltaMs: Long,
        endDeltaMs: Long,
    ): AudioTiming {
        val movedStart = (timing.timelineStartMs + moveDeltaMs).coerceAtLeast(0L)
        val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(sourceDurationMs)
        // Source shift limits: not before the file start, not before timeline 0, keep min length.
        val lowest = -minOf(timing.trimStartMs, (movedStart * speed).roundToLong())
        val highest = (timing.trimEndMs - minLength - timing.trimStartMs).coerceAtLeast(lowest)
        val sourceShift = (startDeltaMs * speed).roundToLong().coerceIn(lowest, highest)
        val trimStart = timing.trimStartMs + sourceShift
        val trimEnd = (timing.trimEndMs + (endDeltaMs * speed).roundToLong())
            .coerceIn(trimStart + minLength, sourceDurationMs.coerceAtLeast(trimStart + minLength))
        return AudioTiming(movedStart + (sourceShift / speed).roundToLong(), trimStart, trimEnd)
    }

    /**
     * Adjusts a move of a bar spanning [startMs, endMs) so its start or end lands on the nearest
     * of [targetsMs] within [thresholdMs]. Returns the (possibly unchanged) move delta.
     */
    fun snapMove(startMs: Long, endMs: Long, moveDeltaMs: Long, targetsMs: List<Long>, thresholdMs: Long): Long {
        var best = moveDeltaMs
        var bestDistance = thresholdMs + 1
        for (target in targetsMs) {
            for (edge in longArrayOf(startMs + moveDeltaMs, endMs + moveDeltaMs)) {
                val distance = abs(target - edge)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = moveDeltaMs + (target - edge)
                }
            }
        }
        return best
    }

    /** The nearest of [targetsMs] within [thresholdMs] of [edgeMs], or null. */
    fun snapEdge(edgeMs: Long, targetsMs: List<Long>, thresholdMs: Long): Long? =
        targetsMs.filter { abs(it - edgeMs) <= thresholdMs }.minByOrNull { abs(it - edgeMs) }

    /** A clip's trim while its handles are dragged, and whether an edge is snapped. */
    data class TrimPreview(val trimStartMs: Long, val trimEndMs: Long, val snapped: Boolean)

    /**
     * Trim of [clip] (starting at timeline [clipStartMs]) after dragging its start handle by
     * [startDeltaMs] and end handle by [endDeltaMs] (timeline ms). The dragged edge snaps to the
     * nearest of [targetsMs] within [thresholdMs]; the result keeps at least
     * [MIN_CLIP_DURATION_MS] of source and stays inside the source.
     */
    fun trimDrag(
        clip: Clip,
        clipStartMs: Long,
        startDeltaMs: Long,
        endDeltaMs: Long,
        targetsMs: List<Long>,
        thresholdMs: Long,
    ): TrimPreview {
        val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(clip.sourceDurationMs)
        var snapped = false
        fun snap(edgeMs: Long): Long = snapEdge(edgeMs, targetsMs, thresholdMs)?.also { snapped = true } ?: edgeMs
        var trimStart = clip.trimStartMs
        if (startDeltaMs != 0L) {
            val left = snap(clipStartMs + startDeltaMs)
            trimStart = (clip.trimStartMs + ((left - clipStartMs) * clip.averageSpeed()).roundToLong())
                .coerceIn(0L, (clip.trimEndMs - minLength).coerceAtLeast(0L))
        }
        var trimEnd = clip.trimEndMs
        if (endDeltaMs != 0L) {
            val originalRight = clipStartMs + clip.timelineDurationMs()
            val right = snap(originalRight + endDeltaMs)
            trimEnd = (clip.trimEndMs + ((right - originalRight) * clip.averageSpeed()).roundToLong())
                .coerceIn(trimStart + minLength, clip.sourceDurationMs.coerceAtLeast(trimStart + minLength))
        }
        return TrimPreview(trimStart, trimEnd, snapped)
    }

    /**
     * Scroll speed (px per frame, negative = left) while a drag is at [x] in a viewport
     * [widthPx] wide: zero in the middle, up to [maxSpeedPx] within [edgePx] of either edge.
     */
    fun autoScrollSpeed(x: Float, widthPx: Float, edgePx: Float, maxSpeedPx: Float): Float = when {
        edgePx <= 0f -> 0f
        x < edgePx -> -maxSpeedPx * ((edgePx - x) / edgePx).coerceAtMost(1f)
        x > widthPx - edgePx -> maxSpeedPx * ((x - (widthPx - edgePx)) / edgePx).coerceAtMost(1f)
        else -> 0f
    }

    /** A ruler mark: [label] is null for minor ticks. */
    data class Tick(val ms: Long, val label: TickLabel?)

    sealed interface TickLabel {
        /** A whole second, shown as a time. */
        data class Time(val ms: Long) : TickLabel

        /** Frame [frame] (1..29) within its second, shown as "Nf". */
        data class Frame(val frame: Int) : TickLabel
    }

    /** CapCut's ruler counts frames at 30 fps when zoomed in. */
    const val RULER_FPS = 30

    private val FRAME_STEPS = intArrayOf(1, 2, 3, 5, 10, 15)

    /**
     * Ruler marks up to [totalMs], labels at least [minSpacingPx] apart. Zoomed out: a labelled
     * tick per interval (whole seconds) and unlabelled ticks every quarter interval. Zoomed in far enough that half a second
     * fits, ticks fall on frames and every tick is labelled (seconds as times, others as "Nf").
     */
    fun rulerTicks(pxPerMs: Float, minSpacingPx: Float, totalMs: Long): List<Tick> {
        val frameMs = 1000f / RULER_FPS
        val frameStep = FRAME_STEPS.firstOrNull { it * frameMs * pxPerMs >= minSpacingPx }
        if (frameStep != null) {
            return buildList {
                var second = 0L
                while (second * 1000 <= totalMs) {
                    for (frame in 0 until RULER_FPS step frameStep) {
                        val ms = second * 1000 + (frame * frameMs).roundToLong()
                        if (ms > totalMs) break
                        add(Tick(ms, if (frame == 0) TickLabel.Time(ms) else TickLabel.Frame(frame)))
                    }
                    second++
                }
            }
        }
        val interval = tickIntervalMs(pxPerMs, minSpacingPx).coerceAtLeast(1_000L)
        val quarter = interval / 4
        return buildList {
            var ms = 0L
            while (ms <= totalMs) {
                add(Tick(ms, if (ms % interval == 0L) TickLabel.Time(ms) else null))
                ms += quarter
            }
        }
    }

    /** Target slot when a reorder tile is dropped with its centre at [centerX] in a row of [count] tiles. */
    fun reorderTargetIndex(centerX: Float, tileWidthPx: Float, count: Int): Int {
        if (count <= 0 || tileWidthPx <= 0f) return 0
        return (centerX / tileWidthPx).toInt().coerceIn(0, count - 1)
    }
}
