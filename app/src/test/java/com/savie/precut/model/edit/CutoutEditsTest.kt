package com.savie.precut.model.edit

import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.OverlayClip
import com.savie.precut.model.frame.Cutout
import com.savie.precut.model.frame.cutoutCoversClip
import com.savie.precut.model.frame.cutoutRangeMs
import com.savie.precut.model.frame.matteMsAt
import com.savie.precut.model.frame.matteMsAtOffset
import com.savie.precut.model.mapMediaUris
import com.savie.precut.model.mediaUris
import com.savie.precut.model.project
import com.savie.precut.model.template.TemplateCategory
import com.savie.precut.model.template.toTemplate
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutEditsTest {

    private val clip = videoClip("a", trimStartMs = 2_000L, trimEndMs = 6_000L)
    private val cutout = Cutout(MATTE, clip.uri, startMs = 2_000L, endMs = 6_000L)
    private val base = project(clip).copy(overlays = listOf(OverlayClip(videoClip("o"), 0L)))

    @Test
    fun setCutout_clampsAndRemoves() {
        val set = EditReducer.reduce(base, EditAction.SetCutout("a", cutout.copy(softness = 3f, edge = -4f))).clips[0].cutout
        assertEquals(cutout.copy(softness = 1f, edge = -1f), set)
        val cleared = EditReducer.reduce(base.copy(clips = listOf(clip.copy(cutout = cutout))), EditAction.SetCutout("a", null))
        assertNull(cleared.clips[0].cutout)
    }

    @Test
    fun setCutout_worksOnOverlays() {
        val overlayCut = Cutout(MATTE, "content://media/o", 0L, 10_000L)
        val set = EditReducer.reduce(base, EditAction.SetCutout("o", overlayCut))
        assertEquals(overlayCut, set.overlays[0].clip.cutout)
    }

    @Test
    fun splitAndDuplicate_keepTheMatte() {
        val cut = base.copy(clips = listOf(clip.copy(cutout = cutout)))
        val split = EditReducer.reduce(cut, EditAction.SplitClip(atTimelineMs = 1_000L, newClipId = "b"))
        assertEquals(listOf(cutout, cutout), split.clips.map { it.cutout })
        assertTrue(split.clips.all { it.cutoutCoversClip() })
        val duplicated = EditReducer.reduce(cut, EditAction.DuplicateClip("a", "c"))
        assertEquals(cutout, duplicated.clips.last().cutout)
    }

    @Test
    fun replace_dropsTheMatte() {
        val cut = base.copy(clips = listOf(clip.copy(cutout = cutout)))
        val info = MediaInfo(ClipType.VIDEO, durationMs = 8_000L, width = 1920, height = 1080, rotationDegrees = 0)
        val replaced = EditReducer.reduce(cut, EditAction.ReplaceClip("a", "content://media/new", info))
        assertNull(replaced.clips[0].cutout)
    }

    @Test
    fun matteTime_isSourceTimeWithinTheRange() {
        val cut = clip.copy(cutout = cutout)
        assertEquals(3_000L, cut.matteMsAt(3_000L))
        // A frame's slack at the ends; beyond it, uncut.
        assertEquals(6_020L, cut.matteMsAt(6_020L))
        assertNull(cut.matteMsAt(7_000L))
        assertNull(cut.matteMsAt(1_000L))
        // A matte of other media (after Replace) never applies.
        assertNull(cut.copy(uri = "content://media/other").matteMsAt(3_000L))
        assertNull(clip.matteMsAt(3_000L))
    }

    @Test
    fun matteTime_followsSpeed() {
        val fast = clip.copy(speed = 2f, cutout = cutout)
        // 1 s into the clip at 2x is 2 s into the trim.
        assertEquals(4_000L, fast.matteMsAtOffset(1_000L))
        assertEquals(2_000L, fast.matteMsAtOffset(-50L))
    }

    @Test
    fun reversedClip_readsItsOriginalsMatteBackwards() {
        val cut = base.copy(clips = listOf(clip.copy(cutout = cutout)))
        val info = MediaInfo(ClipType.VIDEO, durationMs = 4_000L, width = 1920, height = 1080, rotationDegrees = 0)
        val reversed = EditReducer.reduce(cut, EditAction.ReverseClip("a", "file:///reversed/a.mp4", info)).clips[0]
        assertEquals(6_000L, reversed.matteMsAt(0L))
        assertEquals(5_000L, reversed.matteMsAt(1_000L))
        assertEquals(2_000L, reversed.matteMsAt(4_000L))
        assertTrue(reversed.cutoutCoversClip())
        // Back to the original: the matte applies as made.
        val back = EditReducer.reduce(cut.copy(clips = listOf(reversed)), EditAction.UnreverseClip("a")).clips[0]
        assertEquals(3_000L, back.matteMsAt(3_000L))
    }

    @Test
    fun coverage_endsWhenTheTrimGrowsPastTheMatte() {
        val cut = base.copy(clips = listOf(clip.copy(cutout = cutout)))
        val shorter = EditReducer.reduce(cut, EditAction.TrimClip("a", 3_000L, 5_000L)).clips[0]
        assertTrue(shorter.cutoutCoversClip())
        val longer = EditReducer.reduce(cut, EditAction.TrimClip("a", 1_000L, 6_000L)).clips[0]
        assertFalse(longer.cutoutCoversClip())
        assertEquals(1_000L..6_000L, longer.cutoutRangeMs())
    }

    @Test
    fun photo_hasOneMatteFrame() {
        val photo = clip.copy(type = ClipType.IMAGE, trimStartMs = 0L, trimEndMs = 3_000L)
        val cut = photo.copy(cutout = Cutout(MATTE, photo.uri, 0L, 0L))
        assertEquals(0L, cut.matteMsAt(2_000L))
        assertEquals(0L, cut.matteMsAtOffset(2_500L))
        assertTrue(cut.cutoutCoversClip())
        assertEquals(0L..0L, cut.cutoutRangeMs())
    }

    @Test
    fun matte_isProjectMediaAndMovesWithIt() {
        val cut = base.copy(clips = listOf(clip.copy(cutout = cutout)))
        assertTrue(MATTE in cut.mediaUris())
        val moved = cut.mapMediaUris { "moved:$it" }.clips[0]
        assertEquals("moved:$MATTE", moved.cutout?.matteUri)
        // The source moves the same way as the clip's own media, so the matte still matches.
        assertEquals(moved.uri, moved.cutout?.sourceUri)
        assertEquals(3_000L, moved.matteMsAt(3_000L))
    }

    @Test
    fun templateSlots_startWithoutAMatte() {
        val cut = base.copy(clips = listOf(clip.copy(cutout = cutout)))
        val template = cut.toTemplate("t", "T", TemplateCategory.OTHER, slotIds = setOf("a"), textIds = emptySet(), nowMs = 0L)
        assertNull(template.project.clips[0].cutout)
        val kept = cut.toTemplate("t", "T", TemplateCategory.OTHER, slotIds = emptySet(), textIds = emptySet(), nowMs = 0L)
        assertEquals(cutout, kept.project.clips[0].cutout)
    }

    private companion object {
        const val MATTE = "file:///data/mattes/a.matte"
    }
}
