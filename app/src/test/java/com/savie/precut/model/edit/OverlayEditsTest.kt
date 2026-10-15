package com.savie.precut.model.edit

import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.OverlayClip
import com.savie.precut.model.clipById
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.overlayFromMedia
import com.savie.precut.model.overlayLanes
import com.savie.precut.model.placementWithin
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import com.savie.precut.model.withMediaSubstitutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayEditsTest {

    private val pip = OverlayClip(videoClip("o", 6_000L), timelineStartMs = 2_000L)
    // Main: two 10 s clips; one 6 s overlay from 2 s.
    private val base = project(videoClip("a"), videoClip("b")).copy(overlays = listOf(pip))

    private fun reduce(action: EditAction) = EditReducer.reduce(base, action)

    @Test
    fun newOverlay_isSmallerAndCentred() {
        val added = overlayFromMedia("n", "content://n", MediaInfo(ClipType.IMAGE, 0L, 10, 10, 0), atMs = -5L)
        assertEquals(0L, added.timelineStartMs)
        assertEquals(0.6f, added.clip.transform.scale)
    }

    @Test
    fun clipActions_workOnOverlaysById() {
        val faded = reduce(EditAction.SetOpacity("o", 0.4f))
        assertEquals(0.4f, faded.overlays.single().clip.opacity)
        assertEquals(0.4f, faded.clipById("o")?.opacity)
        assertTrue(reduce(EditAction.RemoveItems(setOf("o"), emptySet())).overlays.isEmpty())
    }

    @Test
    fun timing_movesAndTrims_withinBounds() {
        val moved = reduce(EditAction.SetOverlayTiming("o", -100L, 1_000L, 99_000L)).overlays.single()
        assertEquals(0L, moved.timelineStartMs)
        assertEquals(1_000L to 6_000L, moved.clip.trimStartMs to moved.clip.trimEndMs)
    }

    @Test
    fun splitAndDuplicate() {
        val split = reduce(EditAction.SplitOverlay("o", 5_000L, "o2")).overlays
        assertEquals(listOf(2_000L to 5_000L, 5_000L to 8_000L), split.map { it.timelineStartMs to it.timelineEndMs })
        assertSame(base, reduce(EditAction.SplitOverlay("o", 2_050L, "o2")))
        val dup = reduce(EditAction.DuplicateOverlay("o", "o2")).overlays
        assertEquals(8_000L, dup[1].timelineStartMs)
        assertEquals(2, dup.size)
    }

    @Test
    fun switchingTracks() {
        val toMain = reduce(EditAction.OverlayToMain("o", 1))
        assertEquals(listOf("a", "o", "b"), toMain.clips.map { it.id })
        assertTrue(toMain.overlays.isEmpty())
        val toOverlay = reduce(EditAction.ClipToOverlay("b"))
        assertEquals(listOf("a"), toOverlay.clips.map { it.id })
        assertEquals(10_000L, toOverlay.overlays.last().timelineStartMs)
        // The main track keeps at least one clip.
        val single = project(videoClip("only"))
        assertSame(single, EditReducer.reduce(single, EditAction.ClipToOverlay("only")))
    }

    @Test
    fun lanesRangesAndPlacement() {
        val second = OverlayClip(videoClip("p", 4_000L), timelineStartMs = 5_000L)
        val p = base.copy(overlays = listOf(pip, second))
        assertEquals(listOf(listOf("o"), listOf("p")), p.overlayLanes().map { lane -> lane.map { it.id } })
        assertEquals(2_000L until 8_000L, p.clipTimelineRange("o"))
        assertEquals(10_000L until 20_000L, p.clipTimelineRange("b"))
        assertNull(p.clipTimelineRange("nope"))
        // Cut at the video's end; gone when it starts after it.
        assertEquals(1_000L, OverlayClip(videoClip("late", 6_000L), 19_000L).placementWithin(20_000L)?.trimEndMs)
        assertNull(OverlayClip(videoClip("after"), 20_000L).placementWithin(20_000L))
    }

    @Test
    fun proxiesSwapOverlayMediaToo() {
        val swapped = base.withMediaSubstitutes(mapOf("content://media/o" to "file:///proxy.mp4"))
        assertEquals("file:///proxy.mp4", swapped.overlays.single().clip.uri)
    }
}
