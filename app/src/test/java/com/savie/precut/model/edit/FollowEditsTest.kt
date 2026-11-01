package com.savie.precut.model.edit

import com.savie.precut.model.TextOverlay
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.frame.PicturePoint
import com.savie.precut.model.frame.SubjectSample
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowEditsTest {
    /** A 16:9 clip in a square frame. */
    private val clip = videoClip("v").copy(sourceWidth = 1920, sourceHeight = 1080)
    private val text = TextOverlay(id = "t", text = "Hi", startMs = 0L, endMs = 4_000L, xNorm = 0.5f, yNorm = 0.2f)

    private fun walk(fromX: Float, toX: Float) = (0..8).map { i ->
        SubjectSample(i * 500L, PicturePoint(fromX + (toX - fromX) * i / 8f, 0.5f))
    }

    @Test
    fun `the text keeps its offset from the person as they cross the frame`() {
        val followed = requireNotNull(text.followingPerson(clip, 0L, walk(0.5f, 0.75f), frameAspect = 1f))
        val frames = followed.keyframes
        assertTrue(frames.size >= 2)
        // Fitted: the 16:9 picture is the frame's full width, so x moves 0.5 → 0.75.
        assertEquals(0.5f, frames.first().xNorm, 0.02f)
        assertEquals(0.75f, frames.last().xNorm, 0.02f)
        assertTrue(frames.all { kotlin.math.abs(it.yNorm - 0.2f) < 0.001f })
    }

    @Test
    fun `a filled clip moves the text further for the same step`() {
        val filled = clip.copy(fit = FrameFit.FILL)
        val followed = requireNotNull(text.followingPerson(filled, 0L, walk(0.5f, 0.6f), frameAspect = 1f))
        // Filled into a square, the picture is 16/9 frames wide: 0.1 of it is 0.178 of the frame.
        assertEquals(0.5f + 0.1f * 16f / 9f, followed.keyframes.last().xNorm, 0.02f)
    }

    @Test
    fun `no person in the text's time leaves nothing to follow`() {
        val nobody = (0..4).map { SubjectSample(it * 500L, null) }
        assertNull(text.followingPerson(clip, 0L, nobody, frameAspect = 1f))
        val later = text.copy(startMs = 20_000L, endMs = 22_000L)
        assertNull(later.followingPerson(clip, 0L, walk(0.2f, 0.8f), frameAspect = 1f))
    }

    @Test
    fun `the keyframes replace the text's own, inside its length`() {
        val project = com.savie.precut.model.project(clip).copy(textOverlays = listOf(text))
        val followed = requireNotNull(text.followingPerson(clip, 0L, walk(0.5f, 0.75f), frameAspect = 1f))
        val edited = EditReducer.reduce(project, EditAction.SetTextKeyframes("t", followed.keyframes + followed.keyframes.first().copy(atMs = 99_000L)))
        assertEquals(followed.keyframes, edited.textOverlays.single().keyframes)
    }
}
