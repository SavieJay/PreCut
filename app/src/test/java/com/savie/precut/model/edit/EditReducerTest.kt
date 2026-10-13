package com.savie.precut.model.edit

import com.savie.precut.model.MAX_CLIP_SPEED
import com.savie.precut.model.MIN_CLIP_SPEED
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.MAX_VOLUME
import com.savie.precut.model.ClipType
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.project
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditReducerTest {

    private val base = project(videoClip("a", 10_000L), videoClip("b", 5_000L))

    private fun trim(clipId: String, start: Long, end: Long) =
        EditReducer.reduce(base, EditAction.TrimClip(clipId, start, end))

    @Test
    fun trim_setsRangeOnTargetClipOnly() {
        val result = trim("a", 1_000L, 4_000L)
        assertEquals(1_000L, result.clips[0].trimStartMs)
        assertEquals(4_000L, result.clips[0].trimEndMs)
        assertSame(base.clips[1], result.clips[1])
    }

    @Test
    fun trim_clampsToSourceBounds() {
        val result = trim("a", -500L, 99_000L).clips[0]
        assertEquals(0L, result.trimStartMs)
        assertEquals(10_000L, result.trimEndMs)
    }

    @Test
    fun trim_enforcesMinimumLength() {
        val collapsed = trim("a", 5_000L, 5_010L).clips[0]
        assertEquals(5_000L, collapsed.trimStartMs)
        assertEquals(5_000L + MIN_CLIP_DURATION_MS, collapsed.trimEndMs)

        val atEnd = trim("a", 10_000L, 10_000L).clips[0]
        assertEquals(10_000L - MIN_CLIP_DURATION_MS, atEnd.trimStartMs)
        assertEquals(10_000L, atEnd.trimEndMs)
    }

    @Test
    fun trim_reversedRange_isClampedToMinimumLength() {
        val result = trim("a", 6_000L, 2_000L).clips[0]
        assertEquals(6_000L, result.trimStartMs)
        assertEquals(6_000L + MIN_CLIP_DURATION_MS, result.trimEndMs)
    }

    @Test
    fun trim_clipShorterThanMinimum_staysWhole() {
        val tiny = project(videoClip("t", 50L))
        val result = EditReducer.reduce(tiny, EditAction.TrimClip("t", 10L, 20L)).clips[0]
        assertEquals(0L, result.trimStartMs)
        assertEquals(50L, result.trimEndMs)
    }

    @Test
    fun noOpEdits_returnSameInstance() {
        assertSame(base, trim("missing", 0L, 1_000L))
        assertSame(base, trim("a", 0L, 10_000L))
    }

    private fun Project.ids() = clips.map { it.id }

    // AddClips

    @Test
    fun addClips_appendsByDefault_andInsertsAtClampedIndex() {
        val c = videoClip("c")
        assertEquals(listOf("a", "b", "c"), EditReducer.reduce(base, EditAction.AddClips(listOf(c))).ids())
        assertEquals(listOf("c", "a", "b"), EditReducer.reduce(base, EditAction.AddClips(listOf(c), atIndex = 0)).ids())
        assertEquals(listOf("a", "b", "c"), EditReducer.reduce(base, EditAction.AddClips(listOf(c), atIndex = 99)).ids())
        assertSame(base, EditReducer.reduce(base, EditAction.AddClips(emptyList())))
    }

    // DeleteClip

    @Test
    fun deleteClip_removesIt_andUnknownIdIsNoOp() {
        assertEquals(listOf("b"), EditReducer.reduce(base, EditAction.DeleteClip("a")).ids())
        assertSame(base, EditReducer.reduce(base, EditAction.DeleteClip("missing")))
    }

    @Test
    fun deleteLastClip_leavesEmptyProject() {
        val single = project(videoClip("a"))
        assertTrue(EditReducer.reduce(single, EditAction.DeleteClip("a")).clips.isEmpty())
    }

    // SplitClip

    @Test
    fun split_cutsClipUnderPlayhead_andKeepsTotalDuration() {
        val result = EditReducer.reduce(base, EditAction.SplitClip(atTimelineMs = 12_000L, newClipId = "b2"))
        assertEquals(listOf("a", "b", "b2"), result.ids())
        assertEquals(0L to 2_000L, result.clips[1].trimStartMs to result.clips[1].trimEndMs)
        assertEquals(2_000L to 5_000L, result.clips[2].trimStartMs to result.clips[2].trimEndMs)
        assertEquals(base.totalDurationMs(), result.totalDurationMs())
    }

    @Test
    fun split_respectsTrimAndSpeed() {
        // Source 2..10 s at 2x -> 4 s on the timeline; 1 s into it is source 4 s.
        val fast = project(videoClip("f", 10_000L, trimStartMs = 2_000L, trimEndMs = 10_000L, speed = 2f))
        val result = EditReducer.reduce(fast, EditAction.SplitClip(1_000L, "f2"))
        assertEquals(2_000L to 4_000L, result.clips[0].trimStartMs to result.clips[0].trimEndMs)
        assertEquals(4_000L to 10_000L, result.clips[1].trimStartMs to result.clips[1].trimEndMs)
        assertEquals(2f, result.clips[1].speed)
        assertEquals(1_000L, result.clips[0].timelineDurationMs())
    }

    @Test
    fun split_tooCloseToAnEdge_isNoOp() {
        assertSame(base, EditReducer.reduce(base, EditAction.SplitClip(50L, "x")))
        assertSame(base, EditReducer.reduce(base, EditAction.SplitClip(9_950L, "x")))
        assertSame(base, EditReducer.reduce(base, EditAction.SplitClip(10_000L, "x"))) // exact boundary
        assertSame(base, EditReducer.reduce(base, EditAction.SplitClip(99_000L, "x"))) // past the end
        assertFalse(EditReducer.canSplit(base, 10_000L))
        assertTrue(EditReducer.canSplit(base, 10_500L))
    }

    // MoveClip

    @Test
    fun move_reordersClips() {
        val three = project(videoClip("a"), videoClip("b"), videoClip("c"))
        assertEquals(listOf("b", "c", "a"), EditReducer.reduce(three, EditAction.MoveClip(0, 2)).ids())
        assertEquals(listOf("c", "a", "b"), EditReducer.reduce(three, EditAction.MoveClip(2, 0)).ids())
        assertEquals(listOf("a", "c", "b"), EditReducer.reduce(three, EditAction.MoveClip(1, 2)).ids())
    }

    @Test
    fun move_invalidOrSameIndex_isNoOp() {
        assertSame(base, EditReducer.reduce(base, EditAction.MoveClip(0, 0)))
        assertSame(base, EditReducer.reduce(base, EditAction.MoveClip(-1, 1)))
        assertSame(base, EditReducer.reduce(base, EditAction.MoveClip(0, 5)))
    }

    // DuplicateClip

    @Test
    fun duplicate_insertsCopyAfterOriginal_withNewId() {
        val trimmed = trim("a", 1_000L, 3_000L)
        val result = EditReducer.reduce(trimmed, EditAction.DuplicateClip("a", "a2"))
        assertEquals(listOf("a", "a2", "b"), result.ids())
        assertEquals(trimmed.clips[0].copy(id = "a2"), result.clips[1])
        assertSame(trimmed, EditReducer.reduce(trimmed, EditAction.DuplicateClip("missing", "x")))
    }

    // Phase 4: speed, filter, rotation, aspect ratio

    @Test
    fun setSpeed_clampsToRange_andIgnoresPhotos() {
        assertEquals(2f, EditReducer.reduce(base, EditAction.SetSpeed("a", 2f)).clips[0].speed)
        assertEquals(50f, EditReducer.reduce(base, EditAction.SetSpeed("a", 50f)).clips[0].speed)
        assertEquals(MAX_CLIP_SPEED, EditReducer.reduce(base, EditAction.SetSpeed("a", 500f)).clips[0].speed)
        assertEquals(MIN_CLIP_SPEED, EditReducer.reduce(base, EditAction.SetSpeed("a", 0.01f)).clips[0].speed)
        val photo = project(clipFromMedia("p", "content://p", MediaInfo(ClipType.IMAGE, 0L, 10, 10, 0)))
        assertSame(photo, EditReducer.reduce(photo, EditAction.SetSpeed("p", 2f)))
    }

    @Test
    fun setLook_andRotate_updateOnlyTheTargetClip() {
        val filtered = EditReducer.reduce(base, EditAction.SetClipLook("b", ClipLook(filterId = "basic.vintage")))
        assertEquals("basic.vintage", filtered.clips[1].look.filterId)
        assertEquals(null, filtered.clips[0].look.filterId)

        var rotated = base
        repeat(3) { rotated = EditReducer.reduce(rotated, EditAction.RotateClip("a")) }
        assertEquals(270, rotated.clips[0].rotationDegrees)
        assertEquals(0, EditReducer.reduce(rotated, EditAction.RotateClip("a")).clips[0].rotationDegrees)
    }

    @Test
    fun setAspectRatio_changesProject_sameRatioIsNoOp() {
        assertEquals(AspectRatio.RATIO_1_1, EditReducer.reduce(base, EditAction.SetAspectRatio(AspectRatio.RATIO_1_1)).aspectRatio)
        assertSame(base, EditReducer.reduce(base, EditAction.SetAspectRatio(base.aspectRatio)))
    }

    // Phase 4: text overlays (timeline is 15 s: clip a 10 s + clip b 5 s)

    private fun text(id: String = "t", start: Long = 1_000L, end: Long = 4_000L) =
        TextOverlay(id = id, text = "Hi", startMs = start, endMs = end)

    @Test
    fun addText_isClampedToTheTimeline() {
        val added = EditReducer.reduce(base, EditAction.AddText(text(start = 14_000L, end = 17_000L))).textOverlays.single()
        assertEquals(14_000L to 15_000L, added.startMs to added.endMs)
        val atEnd = EditReducer.reduce(base, EditAction.AddText(text(start = 15_000L, end = 18_000L))).textOverlays.single()
        assertEquals(15_000L - MIN_CLIP_DURATION_MS to 15_000L, atEnd.startMs to atEnd.endMs)
    }

    @Test
    fun updateText_replacesById_andClampsPosition() {
        val withText = EditReducer.reduce(base, EditAction.AddText(text()))
        val updated = EditReducer.reduce(withText, EditAction.UpdateText(text().copy(text = "Bye", xNorm = 1.5f, yNorm = -1f)))
        assertEquals("Bye", updated.textOverlays.single().text)
        assertEquals(1f to 0f, updated.textOverlays.single().xNorm to updated.textOverlays.single().yNorm)
        assertSame(withText, EditReducer.reduce(withText, EditAction.UpdateText(text("missing"))))
    }

    @Test
    fun deleteText_removesIt() {
        val withText = EditReducer.reduce(base, EditAction.AddText(text()))
        assertTrue(EditReducer.reduce(withText, EditAction.DeleteText("t")).textOverlays.isEmpty())
        assertSame(withText, EditReducer.reduce(withText, EditAction.DeleteText("missing")))
    }

    @Test
    fun shorteningTheTimeline_clampsTexts() {
        val withText = EditReducer.reduce(base, EditAction.AddText(text(start = 9_000L, end = 14_000L)))
        val shorter = EditReducer.reduce(withText, EditAction.DeleteClip("b")) // timeline now 10 s
        assertEquals(9_000L to 10_000L, shorter.textOverlays.single().startMs to shorter.textOverlays.single().endMs)

        val faster = EditReducer.reduce(withText, EditAction.SetSpeed("a", 4f)) // 2.5 s + 5 s = 7.5 s
        val clamped = faster.textOverlays.single()
        assertEquals(7_500L - MIN_CLIP_DURATION_MS to 7_500L, clamped.startMs to clamped.endMs)
    }

    @Test
    fun deletingAllClips_dropsTexts() {
        val withText = EditReducer.reduce(project(videoClip("a")), EditAction.AddText(text()))
        assertTrue(EditReducer.reduce(withText, EditAction.DeleteClip("a")).textOverlays.isEmpty())
    }

    @Test
    fun clipVolume_isClamped_andMuteKeepsVolume() {
        assertEquals(1.5f, EditReducer.reduce(base, EditAction.SetClipVolume("a", 1.5f)).clips[0].volume)
        assertEquals(MAX_VOLUME, EditReducer.reduce(base, EditAction.SetClipVolume("a", 9f)).clips[0].volume)
        assertEquals(0f, EditReducer.reduce(base, EditAction.SetClipVolume("a", -1f)).clips[0].volume)
        val loud = EditReducer.reduce(base, EditAction.SetClipVolume("a", 1.5f))
        val muted = EditReducer.reduce(loud, EditAction.SetClipMuted("a", true)).clips[0]
        assertTrue(muted.muted)
        assertEquals(1.5f, muted.volume)
    }
}
