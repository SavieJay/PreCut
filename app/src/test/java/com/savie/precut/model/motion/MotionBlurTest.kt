package com.savie.precut.model.motion

import com.savie.precut.model.edit.BatchEdits
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionBlurTest {

    private val hd = 1080L * 1920L

    @Test
    fun framesToBlend_followsTheShutterNotTheFrameRate() {
        // Half strength = 50 ms: one interval at 30 fps covers ~33 ms, at 60 fps ~17 ms.
        assertEquals(3, MotionBlur.framesToBlend(0.5f, 33_333L, hd))
        assertEquals(4, MotionBlur.framesToBlend(0.5f, 16_667L, hd))
        assertEquals(1, MotionBlur.framesToBlend(0f, 33_333L, hd))
        assertEquals(1, MotionBlur.framesToBlend(1f, 0L, hd)) // interval not known yet
    }

    @Test
    fun framesToBlend_isCapped_moreSoForLargeFrames() {
        assertEquals(MotionBlur.MAX_FRAMES, MotionBlur.framesToBlend(1f, 16_667L, hd))
        assertEquals(MotionBlur.MAX_FRAMES_LARGE, MotionBlur.framesToBlend(1f, 16_667L, 3840L * 2160L))
    }

    @Test
    fun weights_areEqual_andLimitedToWhatIsKept() {
        val four = MotionBlur.weights(4, available = 5)
        assertEquals(4, four.size)
        assertEquals(1f, four.sum(), 1e-6f)
        // Right after a seek only the current frame exists.
        assertEquals(listOf(1f), MotionBlur.weights(4, available = 0).toList())
        assertEquals(2, MotionBlur.weights(4, available = 1).size)
    }

    @Test
    fun jumps_resetTheBlend() {
        assertTrue(MotionBlur.isJump(1_000_000L, 900_000L, 33_333L)) // backwards
        assertTrue(MotionBlur.isJump(1_000_000L, 1_000_000L, 33_333L)) // same frame again
        assertTrue(MotionBlur.isJump(1_000_000L, 1_200_000L, 33_333L)) // skipped ahead
        assertFalse(MotionBlur.isJump(1_000_000L, 1_033_333L, 33_333L))
        assertFalse(MotionBlur.isJump(1_000_000L, 1_066_000L, 33_333L)) // one dropped frame is fine
    }

    @Test
    fun reducer_clampsAndAppliesToAll() {
        val base = project(videoClip("a"), videoClip("b"))
        assertEquals(1f, EditReducer.reduce(base, EditAction.SetMotionBlur("a", 3f)).clips[0].motionBlur)
        assertEquals(0f, EditReducer.reduce(base, EditAction.SetMotionBlur("a", -1f)).clips[0].motionBlur)
        val all = EditReducer.reduce(base, BatchEdits.motionBlur(base, 0.4f))
        assertTrue(all.clips.all { it.motionBlur == 0.4f })
        // Splitting keeps it on both parts.
        val split = EditReducer.reduce(all, EditAction.SplitClip(5_000L, "a2")).clips
        assertEquals(listOf(0.4f, 0.4f), split.take(2).map { it.motionBlur })
    }
}
