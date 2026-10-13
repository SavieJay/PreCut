package com.savie.precut.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Phase7ModelTest {

    // Clips of 10 s, 4 s and 6 s: boundaries at 0, 10, 14, 20 s.
    private val p = project(videoClip("a"), videoClip("b", 4_000L), videoClip("c", 6_000L))

    @Test
    fun insertIndexAt_isNearestClipBoundary() {
        assertEquals(0, p.insertIndexAt(0L))
        assertEquals(0, p.insertIndexAt(4_999L)) // first half of clip a
        assertEquals(1, p.insertIndexAt(5_000L)) // second half
        assertEquals(1, p.insertIndexAt(11_000L)) // first half of b
        assertEquals(2, p.insertIndexAt(13_000L))
        assertEquals(3, p.insertIndexAt(20_000L)) // the end
        assertEquals(3, p.insertIndexAt(99_000L))
        assertEquals(0, project().insertIndexAt(1_000L))
    }

    @Test
    fun speedScale_isLogarithmic_andRoundsToReadableSteps() {
        val clip = SpeedScale.CLIP
        assertEquals(0f, clip.toSlider(0.1f), 0.0001f)
        assertEquals(1f, clip.toSlider(100f), 0.0001f)
        assertEquals(1f / 3f, clip.toSlider(1f), 0.0001f) // 1x a third of the way along 0.1..100
        assertEquals(1f, clip.fromSlider(1f / 3f))
        assertEquals(0.1f, clip.fromSlider(0f))
        assertEquals(100f, clip.fromSlider(1f))
        assertEquals(10f, clip.fromSlider(2f / 3f))
        // Every preset survives a round trip through the slider.
        listOf(0.1f, 0.5f, 2f, 5f, 10f, 100f).forEach { assertEquals(it, clip.fromSlider(clip.toSlider(it))) }
        assertEquals(4f, SpeedScale.AUDIO.fromSlider(1f))
    }

    @Test
    fun coverSource_defaultChosenFrameOrPhoto() {
        // Default: first clip's first frame.
        assertEquals(CoverSource("content://media/a", ClipType.VIDEO, 0L, 0), p.coverSource())
        // A frame at 12 s is 2 s into clip b.
        val frame = p.copy(cover = ProjectCover.Frame(12_000L)).coverSource()
        assertEquals(CoverSource("content://media/b", ClipType.VIDEO, 2_000L, 0), frame)
        val photo = p.copy(cover = ProjectCover.Image("content://photo", 90)).coverSource()
        assertEquals(CoverSource("content://photo", ClipType.IMAGE, 0L, 90), photo)
        assertNull(project().coverSource())
    }
}
