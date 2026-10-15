package com.savie.precut.model.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMathTest {

    private val eps = 1e-4f

    @Test
    fun inAndOut_shrinkToFitTheClip() {
        assertEquals(400L to 600L, MotionMath.fittedDurations(400L, 600L, 2_000L))
        assertEquals(400L to 600L, MotionMath.fittedDurations(800L, 1_200L, 1_000L))
        assertEquals(0L to 0L, MotionMath.fittedDurations(0L, 0L, 1_000L))
    }

    @Test
    fun inAnimation_startsAwayAndSettles() {
        val fadeIn = ClipAnimation(AnimationType.FADE, 500L)
        assertEquals(0f, MotionMath.pose(fadeIn, null, null, 0L, 3_000L).opacity, eps)
        assertEquals(Pose.REST, MotionMath.pose(fadeIn, null, null, 500L, 3_000L))
        val slideUp = MotionMath.enterPose(AnimationType.SLIDE_UP, 0f)
        assertEquals(1f, slideUp.offsetY, eps) // starts a frame below
        assertEquals(Pose.REST, MotionMath.enterPose(AnimationType.SLIDE_UP, 1f))
    }

    @Test
    fun outAnimation_leavesTheOtherWay() {
        val out = ClipAnimation(AnimationType.SLIDE_UP, 1_000L)
        assertEquals(Pose.REST, MotionMath.pose(null, out, null, 1_000L, 3_000L))
        // At the very end the clip has left upwards.
        assertEquals(-1f, MotionMath.pose(null, out, null, 3_000L, 3_000L).offsetY, eps)
        assertEquals(360f, MotionMath.leavePose(AnimationType.SPIN, 0f).rotationDegrees, eps)
    }

    @Test
    fun combo_loopsAndOverridesInOut() {
        val rock = ComboAnimation(ComboType.ROCK, speed = 1f)
        val quarter = MotionMath.COMBO_PERIOD_MS / 4
        assertEquals(8f, MotionMath.pose(ClipAnimation(AnimationType.FADE), null, rock, quarter, 5_000L).rotationDegrees, 1e-3f)
        assertEquals(
            MotionMath.comboPose(rock, quarter).rotationDegrees,
            MotionMath.comboPose(rock, quarter + MotionMath.COMBO_PERIOD_MS).rotationDegrees,
            1e-3f,
        )
        // Twice the speed reaches the same point in half the time.
        assertEquals(8f, MotionMath.comboPose(rock.copy(speed = 2f), quarter / 2).rotationDegrees, 1e-3f)
    }

    @Test
    fun transitions_peakAtTheCut() {
        assertEquals(1f, MotionMath.transitionLook(TransitionType.FADE_BLACK, 0.5f).colorMix, eps)
        assertEquals(0f, MotionMath.transitionLook(TransitionType.FADE_BLACK, 0f).colorMix, eps)
        assertTrue(MotionMath.transitionLook(TransitionType.FADE_WHITE, 0.25f).mixWhite)
        assertEquals(180f, MotionMath.transitionLook(TransitionType.SPIN, 0.5f).rotationDegrees, eps)
        assertEquals(360f, MotionMath.transitionLook(TransitionType.SPIN, 1f).rotationDegrees, eps)
        assertEquals(2f, MotionMath.transitionLook(TransitionType.ZOOM_IN, 0.5f).scale, eps)
    }

    @Test
    fun transitionAt_mapsHeadAndTailHalves() {
        // 1 s incoming transition (0.5 s at the start), 400 ms outgoing (0.2 s at the end) on a 3 s clip.
        val head = MotionMath.transitionAt(TransitionType.BLUR, 1_000L, TransitionType.FLASH, 400L, 0L, 3_000L)
        assertEquals(TransitionType.BLUR to 0.5f, head)
        assertEquals(1f, MotionMath.transitionAt(TransitionType.BLUR, 1_000L, null, 0L, 499L, 3_000L)?.second ?: 0f, 0.01f)
        assertNull(MotionMath.transitionAt(TransitionType.BLUR, 1_000L, TransitionType.FLASH, 400L, 1_500L, 3_000L))
        val tail = MotionMath.transitionAt(null, 0L, TransitionType.FLASH, 400L, 2_800L, 3_000L)
        assertEquals(TransitionType.FLASH to 0f, tail)
        assertEquals(0.5f, MotionMath.transitionAt(null, 0L, TransitionType.FLASH, 400L, 3_000L, 3_000L)?.second ?: 0f, eps)
    }

    @Test
    fun transitionDuration_fitsTheShorterClip() {
        val t = ClipTransition(TransitionType.FLASH, 1_500L)
        assertEquals(800L, MotionMath.fittedTransitionMs(t, 800L, 5_000L))
        assertEquals(1_500L, MotionMath.fittedTransitionMs(t, 5_000L, 5_000L))
        assertEquals(MAX_TRANSITION_MS, MotionMath.fittedTransitionMs(t.copy(durationMs = 9_000L), 10_000L, 10_000L))
    }
}
