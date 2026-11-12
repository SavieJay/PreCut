package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.AudioClip
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.keyframe.TextKeyframe
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayheadSnapTest {

    private val twoClips = project(videoClip("a"), videoClip("b"))

    @Test
    fun `clip starts and the end are snap points`() {
        assertEquals(listOf(0L, 10_000L, 20_000L), playheadSnapPoints(twoClips, TimelineSelection()))
    }

    @Test
    fun `beats count only where the audio plays`() {
        // Trimmed to 2..8 s of its source and starting at 1 s, it plays 1..7 s on the timeline: the
        // beat at source 3 s lands at 2 s; source 1 s (before the trim) and 9 s (after it) don't count.
        val music = AudioClip("m", "content://m", sourceDurationMs = 10_000, timelineStartMs = 1_000, trimStartMs = 2_000, trimEndMs = 8_000, beatsMs = listOf(1_000, 3_000, 9_000))
        val points = playheadSnapPoints(twoClips.copy(audioClips = listOf(music)), TimelineSelection())
        assertEquals(listOf(0L, 2_000L, 10_000L, 20_000L), points)
    }

    @Test
    fun `keyframes count only for the selected text`() {
        val key = TextKeyframe(atMs = 1_500, xNorm = 0.5f, yNorm = 0.5f, textSizeSp = 24f, rotationDegrees = 0f, opacity = 1f)
        val title = TextOverlay("t", "Hi", startMs = 3_000, endMs = 6_000, keyframes = listOf(key))
        val withText = twoClips.copy(textOverlays = listOf(title))
        assertFalse(4_500L in playheadSnapPoints(withText, TimelineSelection()))
        assertTrue(4_500L in playheadSnapPoints(withText, TimelineSelection(textId = "t")))
    }

    @Test
    fun `a scrub near a point lands on it, elsewhere it's unchanged`() {
        val points = listOf(0L, 10_000L, 20_000L)
        assertEquals(10_000L, snapScrub(9_950, points, slopMs = 80))
        assertEquals(10_000L, snapScrub(10_080, points, slopMs = 80))
        assertEquals(9_900L, snapScrub(9_900, points, slopMs = 80))
        assertEquals(5_000L, snapScrub(5_000, emptyList(), slopMs = 80))
    }

    @Test
    fun `the nearest of two close points wins`() {
        assertEquals(1_040L, snapScrub(1_030, listOf(1_000L, 1_040L), slopMs = 80))
    }
}
