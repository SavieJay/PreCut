package com.savie.precut.model.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ReframeTest {

    @Test
    fun toContent_followsMirrorTurnAndCrop() {
        val p = PicturePoint(0.2f, 0.3f)
        assertEquals(p, Reframe.toContent(p, mirrored = false, rotationDegrees = 0, crop = null))
        assertEquals(PicturePoint(0.8f, 0.3f), Reframe.toContent(p, mirrored = true, rotationDegrees = 0, crop = null))
        // A clockwise quarter turn: the picture's left edge becomes the top.
        assertEquals(PicturePoint(0.7f, 0.2f), Reframe.toContent(p, mirrored = false, rotationDegrees = 90, crop = null))
        assertEquals(PicturePoint(0.8f, 0.7f), Reframe.toContent(p, mirrored = false, rotationDegrees = 180, crop = null))
        assertEquals(PicturePoint(0.3f, 0.8f), Reframe.toContent(p, mirrored = false, rotationDegrees = 270, crop = null))
        val cropped = Reframe.toContent(p, mirrored = false, rotationDegrees = 0, crop = CropRect(0.1f, 0.1f, 0.5f, 0.5f))
        assertEquals(0.25f, cropped.x, 1e-5f)
        assertEquals(0.5f, cropped.y, 1e-5f)
        // Outside the crop: held at its edge.
        assertEquals(0f, Reframe.toContent(PicturePoint(0f, 0.3f), false, 0, CropRect(0.1f, 0.1f, 0.5f, 0.5f)).x)
    }

    @Test
    fun filledSize_andCentring() {
        // 16:9 filled into 9:16: about 3.16 frames wide.
        val (w, h) = Reframe.filledSize(16f / 9f, 9f / 16f, scale = 1f)
        assertEquals(3.16f, w, 0.01f)
        assertEquals(1f, h, 1e-6f)
        assertEquals(0f, Reframe.centringOffset(0.5f, w), 1e-6f)
        assertEquals(0.79f, Reframe.centringOffset(0.25f, w), 0.01f)
        // Never so far that the picture stops covering the frame.
        assertEquals((w - 1f) / 2f, Reframe.centringOffset(0f, w), 1e-6f)
        assertEquals(0f, Reframe.centringOffset(0.1f, 1f), 1e-6f)
        assertEquals(0.25f, Reframe.centredAt(Reframe.centringOffset(0.25f, w), w), 1e-5f)
    }

    private fun times(count: Int, stepMs: Long = 200L) = LongArray(count) { it * stepMs }

    @Test
    fun path_holdsStillInsideTheDeadZone() {
        val n = 20
        val x = FloatArray(n) { if (it % 2 == 0) 0.02f else -0.02f }
        val path = Reframe.path(times(n), x, FloatArray(n), ReframeSpeed.NORMAL)
        assertTrue(path.all { it.offsetX == path[0].offsetX })
    }

    @Test
    fun path_pansNoFasterThanTheSpeedAllows() {
        val n = 40
        // The person jumps across the picture after 1 s.
        val x = FloatArray(n) { if (it < 5) 0.6f else -0.6f }
        val path = Reframe.path(times(n), x, FloatArray(n), ReframeSpeed.NORMAL)
        for (i in 1 until n) {
            assertTrue(abs(path[i].offsetX - path[i - 1].offsetX) <= ReframeSpeed.NORMAL.maxSpeedPerSecond * 0.2f + 1e-5f)
        }
        // It gets there in the end, short of the target by at most the dead zone.
        assertTrue(abs(path.last().offsetX - -0.6f) <= ReframeSpeed.NORMAL.deadZone + 1e-5f)
        // Slow pans more gently than Fast.
        val slow = Reframe.path(times(n), x, FloatArray(n), ReframeSpeed.SLOW)
        val fast = Reframe.path(times(n), x, FloatArray(n), ReframeSpeed.FAST)
        assertTrue(slow[15].offsetX > fast[15].offsetX)
    }

    @Test
    fun path_holdsThroughGaps_andIsEmptyWithNobody() {
        val x = floatArrayOf(Float.NaN, 0.3f, Float.NaN, Float.NaN, 0.3f)
        val path = Reframe.path(times(5), x, FloatArray(5), ReframeSpeed.FAST)
        assertTrue(path.all { it.offsetX == 0.3f })
        val nobody = FloatArray(4) { Float.NaN }
        assertTrue(Reframe.path(times(4), nobody, nobody, ReframeSpeed.NORMAL).isEmpty())
    }

    @Test
    fun simplify_keepsCornersAndDropsTheRest() {
        val line = (0..10).map { PathPoint(it * 100L, it * 0.05f, 0f) }
        assertEquals(listOf(line.first(), line.last()), Reframe.simplify(line))
        val flat = (0..10).map { PathPoint(it * 100L, 0.2f + (it % 2) * 0.001f, 0f) }
        assertEquals(listOf(flat.first()), Reframe.simplify(flat))
        // Up to 0.5 at 500 ms, then back down.
        val corner = (0..10).map { PathPoint(it * 100L, if (it <= 5) it * 0.1f else 1f - it * 0.1f, 0f) }
        assertEquals(listOf(0L, 500L, 1_000L), Reframe.simplify(corner).map { it.timeMs })
    }

    @Test
    fun valueAt_isStraightBetweenPoints() {
        val path = listOf(PathPoint(0L, 0f, 0f), PathPoint(1_000L, 1f, -1f))
        assertEquals(PathPoint(250L, 0.25f, -0.25f), Reframe.valueAt(path, 250L))
        assertEquals(PathPoint(2_000L, 1f, -1f), Reframe.valueAt(path, 2_000L))
        assertEquals(PathPoint(-5L, 0f, 0f), Reframe.valueAt(path, -5L))
    }
}
