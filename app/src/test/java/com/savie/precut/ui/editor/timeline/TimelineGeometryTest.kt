package com.savie.precut.ui.editor.timeline

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
}
