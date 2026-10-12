package com.savie.precut.model.edit

import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
}
