package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.edit.MIN_CLIP_DURATION_MS
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineGeometryTest {

    @Test
    fun msAndPx_roundTrip() {
        val pxPerMs = 0.154f
        assertEquals(1_540f, TimelineGeometry.msToPx(10_000L, pxPerMs), 0.01f)
        assertEquals(10_000L, TimelineGeometry.pxToMs(1_540f, pxPerMs))
    }

    @Test
    fun tickInterval_isSmallestNiceStepWithEnoughSpacing() {
        // 0.1 px/ms: 1 s = 100 px. Need >= 150 px -> 2 s.
        assertEquals(2_000L, TimelineGeometry.tickIntervalMs(pxPerMs = 0.1f, minSpacingPx = 150f))
        // Very zoomed in: 100 ms = 200 px.
        assertEquals(100L, TimelineGeometry.tickIntervalMs(pxPerMs = 2f, minSpacingPx = 150f))
        // Very zoomed out: falls back to the largest step.
        assertEquals(600_000L, TimelineGeometry.tickIntervalMs(pxPerMs = 0.000001f, minSpacingPx = 150f))
    }

    @Test
    fun clampZoom_respectsBounds_forShortProjects() {
        assertEquals(TimelineGeometry.MAX_DP_PER_SECOND, TimelineGeometry.clampZoom(10_000f, 10_000L, 2f))
        assertEquals(TimelineGeometry.MIN_DP_PER_SECOND, TimelineGeometry.clampZoom(0.1f, 10_000L, 2f))
        assertEquals(100f, TimelineGeometry.clampZoom(100f, 10_000L, 2f))
    }

    @Test
    fun clampZoom_keepsLongProjectsUnderMaxContentWidth() {
        val totalMs = 3 * 60 * 60 * 1000L // 3 hours
        val density = 3f
        val zoom = TimelineGeometry.clampZoom(TimelineGeometry.DEFAULT_DP_PER_SECOND, totalMs, density)
        val contentPx = zoom * density * totalMs / 1000f
        assertTrue("content $contentPx px too wide", contentPx <= TimelineGeometry.MAX_CONTENT_PX + 1f)
    }

    @Test
    fun thumbnailTimes_sampleTileCentres_inSourceTime() {
        // 4 s clip trimmed from 2 s at 2x speed -> 2 s on the timeline, drawn 200 px wide.
        val clip = videoClip(sourceDurationMs = 10_000L, trimStartMs = 2_000L, trimEndMs = 6_000L, speed = 2f)
        val times = TimelineGeometry.thumbnailSourceTimesMs(clip, clipWidthPx = 200f, tileWidthPx = 50f)
        assertEquals(listOf(2_500L, 3_500L, 4_500L, 5_500L), times)
    }

    @Test
    fun thumbnailTimes_partialLastTile_isClampedToClipEnd() {
        val clip = videoClip(sourceDurationMs = 1_000L)
        val times = TimelineGeometry.thumbnailSourceTimesMs(clip, clipWidthPx = 120f, tileWidthPx = 50f)
        assertEquals(3, times.size)
        assertEquals(1_000L, times.last())
    }

    @Test
    fun thumbnailTimes_emptyForZeroWidth() {
        assertEquals(emptyList<Long>(), TimelineGeometry.thumbnailSourceTimesMs(videoClip(), 0f, 50f))
    }

    @Test
    fun reorderTarget_isSlotUnderCentre_clamped() {
        assertEquals(0, TimelineGeometry.reorderTargetIndex(-30f, 50f, 4))
        assertEquals(1, TimelineGeometry.reorderTargetIndex(75f, 50f, 4))
        assertEquals(3, TimelineGeometry.reorderTargetIndex(999f, 50f, 4))
    }

    @Test
    fun reorderLayout_centresTiles_andShrinksToFit() {
        val roomy = ReorderLayout.of(widthPx = 1000f, count = 3, maxTilePx = 100f, marginPx = 20f)
        assertEquals(100f, roomy.tilePx)
        assertEquals(350f, roomy.startPx)
        val crowded = ReorderLayout.of(widthPx = 1000f, count = 20, maxTilePx = 100f, marginPx = 20f)
        assertEquals(48f, crowded.tilePx)
        assertEquals(1, crowded.targetIndex(fingerX = 20f + 48f + 10f, count = 20))
    }

    @Test
    fun moveRange_keepsLength_andStaysInsideTimeline() {
        assertEquals(3_000L to 5_000L, TimelineGeometry.moveRange(1_000L, 3_000L, 2_000L, totalMs = 10_000L))
        assertEquals(0L to 2_000L, TimelineGeometry.moveRange(1_000L, 3_000L, -5_000L, totalMs = 10_000L))
        assertEquals(8_000L to 10_000L, TimelineGeometry.moveRange(1_000L, 3_000L, 50_000L, totalMs = 10_000L))
        // Longer than the timeline: pinned at 0 (the reducer then clamps the end).
        assertEquals(0L to 12_000L, TimelineGeometry.moveRange(0L, 12_000L, 500L, totalMs = 10_000L))
    }

    @Test
    fun dragTextRange_moveKeepsLength_andClamps() {
        fun drag(move: Long = 0L, startDelta: Long = 0L, endDelta: Long = 0L) =
            TimelineGeometry.dragTextRange(2_000L, 5_000L, move, startDelta, endDelta, totalMs = 10_000L)
        assertEquals(2_000L to 5_000L, drag())
        assertEquals(4_500L to 7_500L, drag(move = 2_500L))
        assertEquals(0L to 3_000L, drag(move = -9_000L))
        assertEquals(7_000L to 10_000L, drag(move = 9_000L))
    }

    @Test
    fun dragTextRange_handlesRespectMinimumAndBounds() {
        fun drag(move: Long = 0L, startDelta: Long = 0L, endDelta: Long = 0L) =
            TimelineGeometry.dragTextRange(2_000L, 5_000L, move, startDelta, endDelta, totalMs = 10_000L)
        assertEquals(1_000L to 5_000L, drag(startDelta = -1_000L))
        assertEquals(0L to 5_000L, drag(startDelta = -9_000L))
        assertEquals(4_900L to 5_000L, drag(startDelta = 9_000L)) // 100 ms minimum
        assertEquals(2_000L to 10_000L, drag(endDelta = 9_000L))
        assertEquals(2_000L to 2_100L, drag(endDelta = -9_000L))
        // Move then extend the end: 3..6 s, end +1 s.
        assertEquals(3_000L to 7_000L, drag(move = 1_000L, endDelta = 1_000L))
    }

    private val song = TimelineGeometry.AudioTiming(timelineStartMs = 2_000L, trimStartMs = 1_000L, trimEndMs = 9_000L)

    private fun dragSong(move: Long = 0L, start: Long = 0L, end: Long = 0L, speed: Float = 1f) = TimelineGeometry.dragAudio(
        song, sourceDurationMs = 10_000L, speed = speed, moveDeltaMs = move, startDeltaMs = start, endDeltaMs = end,
    )

    @Test
    fun dragAudio_moveShiftsStart_notBeforeZero() {
        assertEquals(song.copy(timelineStartMs = 5_000L), dragSong(move = 3_000L))
        assertEquals(song.copy(timelineStartMs = 0L), dragSong(move = -9_000L))
    }

    @Test
    fun dragAudio_startHandleTrimsAndMovesTogether() {
        assertEquals(TimelineGeometry.AudioTiming(4_000L, 3_000L, 9_000L), dragSong(start = 2_000L))
        // Limited by the file start (1 s of trim) -> -1 s.
        assertEquals(TimelineGeometry.AudioTiming(1_000L, 0L, 9_000L), dragSong(start = -5_000L))
        assertEquals(9_000L - MIN_CLIP_DURATION_MS, dragSong(start = 50_000L).trimStartMs)
    }

    @Test
    fun dragAudio_endHandleTrimsWithinSource() {
        assertEquals(10_000L, dragSong(end = 5_000L).trimEndMs)
        assertEquals(1_000L + MIN_CLIP_DURATION_MS, dragSong(end = -50_000L).trimEndMs)
    }

    @Test
    fun dragAudio_convertsTimelineDeltasWithSpeed() {
        // At 2x, 1 s of timeline is 2 s of source.
        assertEquals(TimelineGeometry.AudioTiming(3_000L, 3_000L, 9_000L), dragSong(start = 1_000L, speed = 2f))
        assertEquals(8_000L, dragSong(end = -500L, speed = 2f).trimEndMs)
    }

    @Test
    fun snapMove_snapsEitherEdgeToNearestTargetWithinThreshold() {
        // Bar 1..3 s moved +1.95 s: start 2.95 s is 50 ms from the 3 s target -> +2 s.
        assertEquals(2_000L, TimelineGeometry.snapMove(1_000L, 3_000L, 1_950L, listOf(3_000L, 9_000L), thresholdMs = 100L))
        // End edge: 3 s + 5.9 s = 8.9 s, 100 ms from 9 s -> +6 s.
        assertEquals(6_000L, TimelineGeometry.snapMove(1_000L, 3_000L, 5_900L, listOf(9_000L), thresholdMs = 100L))
        // Nothing near: unchanged.
        assertEquals(500L, TimelineGeometry.snapMove(1_000L, 3_000L, 500L, listOf(9_000L), thresholdMs = 100L))
    }
}
