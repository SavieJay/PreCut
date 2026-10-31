package com.savie.precut.media.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatteSmootherTest {

    private fun value(bytes: ByteArray, x: Int, y: Int) = bytes[y * W + x].toInt() and 0xFF

    /** Person in the left half. */
    private fun halves(person: Float = 1f, background: Float = 0f) = FloatArray(W * H) { i -> if (i % W < W / 2) person else background }

    @Test
    fun firstFrame_keepsTheOutlineWithASoftEdge() {
        val out = ByteArray(W * H)
        MatteSmoother(W, H).next(halves(), out)
        assertEquals(255, value(out, 2, 4))
        assertEquals(0, value(out, W - 3, 4))
        // The blur leaves a short gradient across the edge, which Edge and Softness move along.
        val left = value(out, W / 2 - 1, 4)
        val right = value(out, W / 2, 4)
        assertTrue(left in 129..254 && right in 1..126)
    }

    @Test
    fun smallFlicker_isDamped() {
        val smoother = MatteSmoother(W, H)
        val out = ByteArray(W * H)
        smoother.next(halves(background = 0.1f), out)
        val steady = value(out, W - 3, 4)
        // The model's noise jumps by 0.1; the matte moves by much less.
        smoother.next(halves(background = 0.2f), out)
        val after = value(out, W - 3, 4)
        assertTrue(after - steady in 1..13)
    }

    @Test
    fun realMovement_comesThroughAtOnce() {
        val smoother = MatteSmoother(W, H)
        val out = ByteArray(W * H)
        smoother.next(halves(), out)
        // The person leaves: a full change passes in one frame.
        smoother.next(FloatArray(W * H), out)
        assertTrue(out.all { it.toInt() == 0 })
        smoother.next(halves(), out)
        assertEquals(255, value(out, 2, 4))
    }

    @Test
    fun quantize_snapsNearCertainValues() {
        assertEquals(0, MatteSmoother.quantize(0.015f).toInt() and 0xFF)
        assertEquals(255, MatteSmoother.quantize(0.99f).toInt() and 0xFF)
        assertEquals(128, MatteSmoother.quantize(0.5f).toInt() and 0xFF)
        assertEquals(0, MatteSmoother.quantize(-3f).toInt() and 0xFF)
    }

    @Test
    fun personShare_countsMostlyPersonPixels() {
        val out = ByteArray(W * H)
        MatteSmoother(W, H).next(halves(), out)
        assertEquals(0.5f, MatteSmoother.personShare(out), 0.001f)
        assertEquals(0f, MatteSmoother.personShare(ByteArray(10) { 127 }), 0f)
    }

    private companion object {
        const val W = 16
        const val H = 8
    }
}
