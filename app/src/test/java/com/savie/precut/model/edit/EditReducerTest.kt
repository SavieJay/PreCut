package com.savie.precut.model.edit

import com.savie.precut.model.Project
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
}
