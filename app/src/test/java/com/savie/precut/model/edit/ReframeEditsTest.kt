package com.savie.precut.model.edit

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.ClipType
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.frame.PicturePoint
import com.savie.precut.model.frame.ReframeSpeed
import com.savie.precut.model.frame.SubjectSample
import com.savie.precut.model.keyframe.ClipKeyframe
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReframeEditsTest {

    /** A 4 s landscape clip. */
    private val landscape = videoClip("a", sourceDurationMs = 4_000L).copy(sourceWidth = 1920, sourceHeight = 1080)
    private val portraitProject = project(landscape).copy(aspectRatio = AspectRatio.RATIO_9_16)

    /** The person at [x] (and the middle height) every 200 ms. */
    private fun samples(x: (Long) -> Float?) = (0L..4_000L step 200L).map { t -> SubjectSample(t, x(t)?.let { PicturePoint(it, 0.5f) }) }

    private fun reframe(samples: List<SubjectSample>, base: com.savie.precut.model.Project = portraitProject) =
        EditReducer.reduce(base, EditAction.AutoReframe("a", samples, ReframeSpeed.NORMAL))

    @Test
    fun stillPerson_becomesAPlainOffset() {
        val clip = reframe(samples { 0.25f }).clips[0]
        assertEquals(FrameFit.FILL, clip.fit)
        assertTrue(clip.keyframes.isEmpty())
        // The person (a quarter across) is brought to the middle of the 9:16 frame.
        assertEquals(0.79f, clip.transform.offsetX, 0.01f)
        assertEquals(0f, clip.transform.offsetY, 1e-6f)
    }

    @Test
    fun movingPerson_isFollowedWithPositionKeyframes() {
        // Walks from the left third to the right third.
        val clip = reframe(samples { t -> 0.3f + 0.4f * t / 4_000f }).clips[0]
        assertTrue(clip.keyframes.size >= 2)
        val first = clip.keyframes.first()
        val last = clip.keyframes.last()
        assertTrue(first.transform.offsetX > 0f)
        assertTrue(last.transform.offsetX < 0f)
        // Source-time keyframes inside the clip, the plain transform starting where the first one does.
        assertTrue(clip.keyframes.all { it.atMs in 0L..4_000L })
        assertEquals(first.transform, clip.transform)
    }

    @Test
    fun otherKeyframedValues_stay() {
        val fading = landscape.copy(
            keyframes = listOf(ClipKeyframe(0L, opacity = 1f), ClipKeyframe(4_000L, opacity = 0f)),
        )
        val clip = reframe(samples { t -> 0.3f + 0.4f * t / 4_000f }, project(fading).copy(aspectRatio = AspectRatio.RATIO_9_16)).clips[0]
        assertEquals(1f, clip.keyframes.first { it.atMs == 0L }.opacity)
        assertEquals(0f, clip.keyframes.first { it.atMs == 4_000L }.opacity)
        // New keyframes in between carry the fade at their moment.
        clip.keyframes.filter { it.atMs in 1L until 4_000L }.forEach { keyframe ->
            assertEquals(1f - keyframe.atMs / 4_000f, keyframe.opacity, 0.02f)
        }
    }

    @Test
    fun zoomedKeyframes_centreTheSamePoint() {
        val zoomed = landscape.copy(keyframes = listOf(ClipKeyframe(0L, transform = ClipTransform(scale = 2f)), ClipKeyframe(4_000L)))
        val clip = reframe(samples { 0.25f }, project(zoomed).copy(aspectRatio = AspectRatio.RATIO_9_16)).clips[0]
        // Twice the size: twice the offset brings the same point to the middle.
        val offset = clip.keyframes.first { it.atMs == 0L }.transform.offsetX
        assertEquals(1.58f, offset, 0.02f)
        assertEquals(2f, clip.keyframes.first { it.atMs == 0L }.transform.scale)
    }

    @Test
    fun nobodyOrNothingToMove_changesNothing() {
        assertSame(portraitProject, reframe(samples { null }))
        // A landscape clip in a landscape frame already fills it exactly.
        val wide = project(landscape).copy(aspectRatio = AspectRatio.RATIO_16_9)
        assertSame(wide, reframe(samples { 0.1f }, wide))
    }

    @Test
    fun photo_getsOneOffset() {
        val photo = landscape.copy(type = ClipType.IMAGE, trimStartMs = 0L, trimEndMs = 3_000L, sourceDurationMs = 3_000L)
        val clip = reframe(listOf(SubjectSample(0L, PicturePoint(0.75f, 0.5f))), project(photo).copy(aspectRatio = AspectRatio.RATIO_9_16)).clips[0]
        assertTrue(clip.keyframes.isEmpty())
        assertEquals(-0.79f, clip.transform.offsetX, 0.01f)
    }

    @Test
    fun applyToAll_isOneUndoStepInABatch() {
        val two = project(landscape, landscape.copy(id = "b")).copy(aspectRatio = AspectRatio.RATIO_9_16)
        val batch = EditAction.Batch(listOf("a", "b").map { EditAction.AutoReframe(it, samples { 0.25f }, ReframeSpeed.NORMAL) })
        val done = EditReducer.reduce(two, batch)
        assertTrue(done.clips.all { it.fit == FrameFit.FILL && it.transform.offsetX > 0.7f })
    }
}
