package com.savie.precut.model.frame

import org.junit.Assert.assertEquals
import org.junit.Test

class LayerMapsTest {

    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, 1e-5f)
        assertEquals(expected.second, actual.second, 1e-5f)
    }

    @Test
    fun unrotate_mapsTurnedCornersBack() {
        // Turned 90° clockwise, the picture's top-left (0, 1 in y-up) lands at the top-right (1, 1).
        assertPoint(0f to 1f, Affine.unrotate(90).apply(1f, 1f))
        assertPoint(1f to 1f, Affine.unrotate(180).apply(0f, 0f))
        // 270° clockwise: the top-left lands at the bottom-left.
        assertPoint(0f to 1f, Affine.unrotate(270).apply(0f, 0f))
        assertPoint(0.3f to 0.7f, Affine.unrotate(360).apply(0.3f, 0.7f))
    }

    @Test
    fun then_appliesInOrder() {
        val map = Affine.UNMIRROR.then(Affine.FLIP_Y)
        assertPoint(0.75f to 0.25f, map.apply(0.25f, 0.75f))
        val turnTwice = Affine.unrotate(90).then(Affine.unrotate(90))
        assertPoint(Affine.unrotate(180).apply(0.2f, 0.9f), turnTwice.apply(0.2f, 0.9f))
    }

    @Test
    fun orientation_cropsAfterMirrorAndTurn() {
        // Crop: the left half (y-down fractions) of the turned picture.
        val orientation = LayerOrientation(mirrored = true, rotationDegrees = 90, crop = CropRect(0f, 0f, 0.5f, 1f))
        assertEquals(500 to 2000, orientation.size(width = 2000, height = 1000))
        // The cropped output's right edge is the turned picture's middle.
        val (x, y) = orientation.map().apply(1f, 0.5f)
        // Turned picture (0.5, 0.5) → mirrored (0.5, 0.5) → original (0.5, 0.5).
        assertPoint(0.5f to 0.5f, x to y)
        // Output bottom-left → turned (0, 0) → mirrored (1, 0) → original (0, 0).
        assertPoint(0f to 0f, orientation.map().apply(0f, 0f))
    }

    @Test
    fun uncrop_usesYDownFractions() {
        val crop = CropRect(left = 0.1f, top = 0.2f, right = 0.5f, bottom = 0.6f)
        // Output top-left is the crop's top-left: x 0.1, y-down 0.2 → y-up 0.8.
        assertPoint(0.1f to 0.8f, Affine.uncrop(crop).apply(0f, 1f))
        assertPoint(0.5f to 0.4f, Affine.uncrop(crop).apply(1f, 0f))
    }

    @Test
    fun sourcePoint_undoesPlacementAndOrientation() {
        // A 1:1 picture filling the middle half of a square frame, turned 90° by the user.
        val placement = ClipPlacement(centerX = 0.5f, centerY = 0.5f, width = 0.5f, height = 0.5f, rotationDegrees = 0f)
        val turned = LayerOrientation(mirrored = false, rotationDegrees = 90, crop = null)
        // The picture's top-left on screen was the source's bottom-left before the turn.
        val (x, y) = sourcePointAt(placement, 100f, 100f, turned, 0.25f + 0.001f, 0.25f + 0.001f)!!
        assertEquals(0f, x, 0.01f)
        assertEquals(1f, y, 0.01f)
        assertEquals(null, sourcePointAt(placement, 100f, 100f, turned, 0.1f, 0.1f))
    }

    @Test
    fun sourcePoint_followsTheClipsTurn() {
        // Placed turned 90° clockwise: the screen point right of centre shows the picture's top.
        val placement = ClipPlacement(centerX = 0.5f, centerY = 0.5f, width = 0.5f, height = 0.5f, rotationDegrees = 90f)
        val plain = LayerOrientation(mirrored = false, rotationDegrees = 0, crop = null)
        val (x, y) = sourcePointAt(placement, 100f, 100f, plain, 0.7f, 0.5f)!!
        assertEquals(0.5f, x, 0.01f)
        assertEquals(0.1f, y, 0.01f)
    }
}
