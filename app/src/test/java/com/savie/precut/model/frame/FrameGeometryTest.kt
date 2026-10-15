package com.savie.precut.model.frame

import com.savie.precut.model.frame.FrameGeometry.CropHandle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameGeometryTest {

    private val eps = 1e-4f

    @Test
    fun placement_fitAndFill_landscapeInPortrait() {
        val fit = FrameGeometry.placement(1920f, 1080f, 1080f, 1920f, FrameFit.FIT, ClipTransform.IDENTITY)
        assertEquals(1f, fit.width, eps)
        assertEquals(1080f * (1080f / 1920f) / 1920f, fit.height, eps)
        assertEquals(0.5f, fit.centerX, eps)
        val fill = FrameGeometry.placement(1920f, 1080f, 1080f, 1920f, FrameFit.FILL, ClipTransform.IDENTITY)
        assertEquals(1f, fill.height, eps)
        assertEquals(1920f * (1920f / 1080f) / 1080f, fill.width, eps)
    }

    @Test
    fun placement_appliesUserTransform_andIgnoresContentScale() {
        val t = ClipTransform(scale = 0.5f, rotationDegrees = 15f, offsetX = 0.1f, offsetY = -0.1f)
        val a = FrameGeometry.placement(1080f, 1920f, 1080f, 1920f, FrameFit.FIT, t)
        assertEquals(0.5f, a.width, eps)
        assertEquals(0.5f, a.height, eps)
        assertEquals(0.6f, a.centerX, eps)
        assertEquals(0.4f, a.centerY, eps)
        assertEquals(15f, a.rotationDegrees, eps)
        // Only the content's shape matters, not its pixel count.
        assertEquals(a, FrameGeometry.placement(9f, 16f, 1080f, 1920f, FrameFit.FIT, t))
    }

    @Test
    fun gesture_snapsToCentreAndRightAngles_andClampsScale() {
        val start = ClipTransform(offsetX = 0.2f)
        val (moved, snaps) = FrameGeometry.applyGesture(start, -0.19f, 0.3f, 1f, 0f, centreSnap = 0.02f)
        assertEquals(0f, moved.offsetX, eps)
        assertEquals(0.3f, moved.offsetY, eps)
        assertTrue(snaps.centreX)
        assertFalse(snaps.centreY)
        val (turned, turnSnaps) = FrameGeometry.applyGesture(ClipTransform(), 0f, 0f, 100f, 87f, 0f)
        assertEquals(90f, turned.rotationDegrees, eps)
        assertTrue(turnSnaps.rotation)
        assertEquals(MAX_CLIP_SCALE, turned.scale, eps)
        assertEquals(-90f, FrameGeometry.normalizeDegrees(270f), eps)
        assertEquals(180f, FrameGeometry.normalizeDegrees(-180f), eps)
    }

    @Test
    fun cropNdc_isYUp() {
        assertArrayEquals(floatArrayOf(-1f, 1f, -1f, 1f), FrameGeometry.cropNdc(CropRect.FULL), eps)
        assertArrayEquals(floatArrayOf(-0.5f, 0.5f, -0.2f, 0.8f), FrameGeometry.cropNdc(CropRect(0.25f, 0.1f, 0.75f, 0.6f)), eps)
    }

    @Test
    fun maxCentredCrop_fitsTheRatio() {
        val square = FrameGeometry.maxCentredCrop(1f, 16f, 9f)
        assertEquals(CropRect((1f - 9f / 16f) / 2f, 0f, (1f + 9f / 16f) / 2f, 1f), square)
        val wide = FrameGeometry.maxCentredCrop(16f / 9f, 9f, 16f)
        assertEquals(1f, wide.width, eps)
        assertEquals((9f / 16f) / (16f / 9f), wide.height, eps)
        assertEquals(CropRect.FULL, FrameGeometry.maxCentredCrop(null, 4f, 3f))
    }

    @Test
    fun rotateAndMirrorCrop_followThePicture() {
        val strip = CropRect(0f, 0f, 0.5f, 0.25f)
        assertEquals(CropRect(0.75f, 0f, 1f, 0.5f), FrameGeometry.rotateCrop(strip))
        var turned = strip
        repeat(4) { turned = FrameGeometry.rotateCrop(turned) }
        assertEquals(strip, turned)
        assertEquals(CropRect(0.5f, 0f, 1f, 0.25f), FrameGeometry.mirrorCrop(strip))
        assertEquals(CropRatio.RATIO_16_9, FrameGeometry.rotateRatio(CropRatio.RATIO_9_16))
        assertEquals(CropRatio.FREE, FrameGeometry.rotateRatio(CropRatio.RATIO_4_5))
    }

    @Test
    fun dragCrop_free_movesEdgesWithinBounds() {
        val start = CropRect(0.2f, 0.2f, 0.6f, 0.6f)
        assertEquals(CropRect(0.2f, 0.2f, 0.7f, 0.7f), FrameGeometry.dragCrop(start, CropHandle.BOTTOM_RIGHT, 0.1f, 0.1f, null, 1f).rounded())
        assertEquals(1f, FrameGeometry.dragCrop(start, CropHandle.RIGHT, 2f, 0f, null, 1f).right, eps)
        assertEquals(0.5f, FrameGeometry.dragCrop(start, CropHandle.LEFT, 0.9f, 0f, null, 1f).left, eps) // min side
        assertEquals(CropRect(0.6f, 0.2f, 1f, 0.6f), FrameGeometry.dragCrop(start, CropHandle.MOVE, 0.8f, 0f, null, 1f).rounded())
    }

    @Test
    fun dragCrop_locked_keepsPixelAspect_andIgnoresEdges() {
        val pictureAspect = 16f / 9f
        val start = FrameGeometry.maxCentredCrop(1f, pictureAspect, 1f)
        val dragged = FrameGeometry.dragCrop(start, CropHandle.TOP_LEFT, 0.1f, 0.1f, 1f, pictureAspect)
        assertEquals(1f, dragged.width * pictureAspect / dragged.height, 1e-3f)
        assertEquals(start.right, dragged.right, eps)
        assertEquals(start.bottom, dragged.bottom, eps)
        assertTrue(dragged.left >= 0f && dragged.top >= 0f)
        assertEquals(start, FrameGeometry.dragCrop(start, CropHandle.LEFT, 0.1f, 0f, 1f, pictureAspect))
    }

    private fun CropRect.rounded() = CropRect(r(left), r(top), r(right), r(bottom))

    private fun r(v: Float) = Math.round(v * 1000f) / 1000f
}
