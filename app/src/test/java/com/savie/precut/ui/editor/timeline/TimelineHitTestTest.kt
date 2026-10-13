package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineHitTestTest {

    // Two 10 s clips at 0.1 px/ms -> clip a spans 0..1000 px, clip b 1000..2000 px on the timeline.
    private val twoClips = project(videoClip("a"), videoClip("b"))

    private fun hit(scrollPx: Int, selected: String? = null) = TimelineHitTest(
        twoClips, selected, pxPerMs = 0.1f, scrollPx = scrollPx, viewportWidthPx = 400f, handleSlopPx = 20f,
        stripYPx = 30f..90f,
    )

    private val y = 60f // inside the clip strip

    @Test
    fun centreOfViewport_isThePlayheadTime() {
        // Scrolled 1500 px: the playhead (x = 200) is at timeline px 1500 = 15 s, inside clip b.
        assertEquals(1, hit(scrollPx = 1500).clipIndexAt(200f, y))
        // Scrolled 500 px: the playhead is at 5 s (clip a); x = 0 is timeline px 300, also clip a.
        assertEquals(0, hit(scrollPx = 500).clipIndexAt(200f, y))
        assertEquals(0, hit(scrollPx = 500).clipIndexAt(0f, y))
    }

    @Test
    fun emptySpaceBeforeAndAfter_hitsNothing() {
        assertNull(hit(scrollPx = 0).clipIndexAt(100f, y)) // timeline px -100
        assertNull(hit(scrollPx = 2000).clipIndexAt(300f, y)) // timeline px 2100
    }

    @Test
    fun trimHandles_areOnlyAtSelectedClipEdges() {
        // Scroll 1000: clip b starts at viewport x = 200 and ends at x = 1200.
        val selectedB = hit(scrollPx = 1000, selected = "b")
        assertTrue(selectedB.isOnTrimHandle(210f))
        assertFalse(selectedB.isOnTrimHandle(300f))
        assertFalse(hit(scrollPx = 1000, selected = null).isOnTrimHandle(200f))
    }

    @Test
    fun touchesAboveOrBelowTheStrip_hitNoClip() {
        assertNull(hit(scrollPx = 500).clipIndexAt(200f, 10f)) // ruler
        assertNull(hit(scrollPx = 500).clipIndexAt(200f, 110f)) // text lane
    }
}
