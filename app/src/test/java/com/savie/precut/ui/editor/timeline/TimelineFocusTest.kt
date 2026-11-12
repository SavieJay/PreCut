package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.AudioClip
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 29E: a text panel keeps the strip and the text lanes. The gutter tiles and the timeline's
 * height are added up separately, so they must agree in every mode.
 */
class TimelineFocusTest {

    // Two overlapping texts need two text lanes; a song adds an audio lane.
    private val edit = project(videoClip("a"), videoClip("b")).copy(
        textOverlays = listOf(TextOverlay("t1", "Hi", startMs = 0, endMs = 4_000), TextOverlay("t2", "There", startMs = 2_000, endMs = 6_000)),
        audioClips = listOf(AudioClip("m", "content://m", sourceDurationMs = 20_000, trimEndMs = 20_000)),
    )

    private fun bottomOfLastLane(focus: TimelineFocus) = gutterLanes(edit, focus).last().let { it.top + it.height }

    @Test
    fun `strip and text shows the video strip and every text lane, no audio`() {
        val lanes = gutterLanes(edit, TimelineFocus.StripAndText)
        assertEquals(3, lanes.size) // video + two text lanes
        assertTrue(lanes.first().video)
        assertEquals(bottomOfLastLane(TimelineFocus.StripAndText) + BottomPadding, timelineHeight(edit, TimelineFocus.StripAndText))
    }

    @Test
    fun `every mode's gutter fits its height`() {
        TimelineFocus.entries.forEach { focus ->
            assertEquals("$focus", bottomOfLastLane(focus) + BottomPadding, timelineHeight(edit, focus))
        }
    }

    @Test
    fun `the modes grow from strip to strip and text to all`() {
        val strip = timelineHeight(edit, TimelineFocus.Strip)
        val text = timelineHeight(edit, TimelineFocus.StripAndText)
        val all = timelineHeight(edit, TimelineFocus.All)
        assertTrue(strip < text && text < all)
        assertEquals(1, gutterLanes(edit, TimelineFocus.Strip).size)
    }
}
