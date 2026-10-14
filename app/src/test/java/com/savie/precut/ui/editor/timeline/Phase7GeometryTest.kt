package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.videoClip
import com.savie.precut.ui.editor.timeline.TimelineGeometry.TickLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7GeometryTest {

    @Test
    fun rulerTicks_zoomedOut_labelIntervalsWithQuarterTicks() {
        // 0.1 px/ms: 1 s = 100 px, minimum spacing 150 px -> 2 s labels, ticks every 0.5 s.
        val ticks = TimelineGeometry.rulerTicks(pxPerMs = 0.1f, minSpacingPx = 150f, totalMs = 4_000L)
        assertEquals((0L..4_000L step 500L).toList(), ticks.map { it.ms })
        assertEquals(listOf(0L, 2_000L, 4_000L), ticks.filter { it.label != null }.map { it.ms })
        assertEquals(TickLabel.Time(2_000L), ticks[4].label)
    }

    @Test
    fun rulerTicks_zoomedIn_countFrames() {
        // 1 px/ms: a frame is 33.3 px; 56 px spacing -> every 2 frames.
        val ticks = TimelineGeometry.rulerTicks(pxPerMs = 1f, minSpacingPx = 56f, totalMs = 1_000L)
        assertEquals(TickLabel.Time(0L), ticks.first().label)
        assertEquals(TickLabel.Frame(2), ticks[1].label)
        assertEquals(67L, ticks[1].ms)
        assertEquals(TickLabel.Time(1_000L), ticks.last().label)
        assertEquals(16, ticks.size) // 15 in the first second, then 1 s
    }

    @Test
    fun snapEdge_picksNearestTargetWithinThreshold() {
        assertEquals(3_000L, TimelineGeometry.snapEdge(2_950L, listOf(1_000L, 3_000L, 3_100L), 100L))
        assertNull(TimelineGeometry.snapEdge(2_500L, listOf(3_000L), 100L))
    }

    @Test
    fun trimDrag_endHandleSnapsToPlayhead() {
        // 10 s clip starting at 5 s; dragging the end -2.95 s lands 50 ms from the playhead at 12 s.
        val trim = TimelineGeometry.trimDrag(videoClip(), clipStartMs = 5_000L, startDeltaMs = 0L, endDeltaMs = -2_950L, targetsMs = listOf(12_000L), thresholdMs = 100L)
        assertEquals(7_000L, trim.trimEndMs)
        assertTrue(trim.snapped)
    }

    @Test
    fun trimDrag_startHandleUsesSpeed_andClamps() {
        // At 2x, 1 s of timeline is 2 s of source.
        val clip = videoClip(speed = 2f)
        val trim = TimelineGeometry.trimDrag(clip, 0L, startDeltaMs = 1_000L, endDeltaMs = 0L, targetsMs = emptyList(), thresholdMs = 0L)
        assertEquals(2_000L, trim.trimStartMs)
        assertFalse(trim.snapped)
        val tooFar = TimelineGeometry.trimDrag(clip, 0L, startDeltaMs = 50_000L, endDeltaMs = 0L, targetsMs = emptyList(), thresholdMs = 0L)
        assertEquals(clip.trimEndMs - 100L, tooFar.trimStartMs)
        val beforeSource = TimelineGeometry.trimDrag(clip, 0L, startDeltaMs = -5_000L, endDeltaMs = 0L, targetsMs = emptyList(), thresholdMs = 0L)
        assertEquals(0L, beforeSource.trimStartMs)
    }

    @Test
    fun autoScrollSpeed_rampsUpNearEdges() {
        assertEquals(0f, TimelineGeometry.autoScrollSpeed(500f, 1000f, 100f, 20f))
        assertEquals(-20f, TimelineGeometry.autoScrollSpeed(0f, 1000f, 100f, 20f))
        assertEquals(-10f, TimelineGeometry.autoScrollSpeed(50f, 1000f, 100f, 20f))
        assertEquals(10f, TimelineGeometry.autoScrollSpeed(950f, 1000f, 100f, 20f))
        assertEquals(20f, TimelineGeometry.autoScrollSpeed(1200f, 1000f, 100f, 20f)) // capped
    }
}
