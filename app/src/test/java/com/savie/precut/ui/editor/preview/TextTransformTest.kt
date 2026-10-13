package com.savie.precut.ui.editor.preview

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTransformTest {

    private val start = TextTransform(xNorm = 0.3f, yNorm = 0.8f, sizeSp = 32f, rotationDegrees = 0f)

    @Test
    fun gesture_movesScalesAndRotates() {
        val (result, snaps) = TextTransform.applyGesture(start, 0.1f, -0.1f, zoom = 2f, rotationDelta = 30f, centreSnapNorm = 0.01f)
        assertEquals(0.4f, result.xNorm, 0.0001f)
        assertEquals(0.7f, result.yNorm, 0.0001f)
        assertEquals(64f, result.sizeSp)
        assertEquals(30f, result.rotationDegrees)
        assertFalse(snaps.centreX || snaps.centreY || snaps.rotation)
    }

    @Test
    fun snapsToCentreLines_andRightAngles() {
        val (result, snaps) = TextTransform.applyGesture(start, 0.195f, -0.295f, zoom = 1f, rotationDelta = 93f, centreSnapNorm = 0.01f)
        assertEquals(0.5f, result.xNorm)
        assertEquals(0.5f, result.yNorm)
        assertEquals(90f, result.rotationDegrees)
        assertTrue(snaps.centreX && snaps.centreY && snaps.rotation)
    }

    @Test
    fun sizeAndPositionAreClamped_rotationWraps() {
        val (result, _) = TextTransform.applyGesture(start, 5f, 5f, zoom = 100f, rotationDelta = 200f, centreSnapNorm = 0f)
        assertEquals(1f, result.xNorm)
        assertEquals(1f, result.yNorm)
        assertEquals(160f, result.sizeSp)
        assertEquals(-160f, result.rotationDegrees)
        assertEquals(-90f, TextTransform.normalizeDegrees(270f))
        assertEquals(180f, TextTransform.normalizeDegrees(-180f))
    }

    @Test
    fun hitTest_accountsForRotation() {
        val center = Offset(100f, 100f)
        val size = Offset(200f, 20f)
        // A wide box rotated 90° is tall: a point 80 px above the centre is on it, 80 px right isn't.
        assertTrue(hitsText(Offset(100f, 20f), center, size, 90f, slopPx = 0f))
        assertFalse(hitsText(Offset(180f, 100f), center, size, 90f, slopPx = 0f))
        assertTrue(hitsText(Offset(180f, 100f), center, size, 0f, slopPx = 0f))
    }
}
