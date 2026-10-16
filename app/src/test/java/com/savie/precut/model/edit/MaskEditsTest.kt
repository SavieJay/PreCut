package com.savie.precut.model.edit

import com.savie.precut.model.OverlayClip
import com.savie.precut.model.frame.ChromaKey
import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.frame.MaskGeometry
import com.savie.precut.model.frame.MaskShape
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaskEditsTest {

    private val base = project(videoClip("a")).copy(overlays = listOf(OverlayClip(videoClip("o"), 0L)))

    @Test
    fun setMask_clampsAndRemoves() {
        val wild = ClipMask(MaskShape.STAR, centerX = 4f, width = 10f, height = 0f, rotationDegrees = 270f, feather = 2f, cornerRadius = -1f)
        val masked = EditReducer.reduce(base, EditAction.SetMask("a", wild)).clips[0].mask!!
        assertEquals(1f, masked.centerX)
        assertEquals(ClipMask.MAX_SIZE, masked.width)
        assertEquals(ClipMask.MIN_SIZE, masked.height)
        assertEquals(-90f, masked.rotationDegrees)
        assertEquals(1f, masked.feather)
        assertEquals(0f, masked.cornerRadius)
        val cleared = EditReducer.reduce(base.copy(clips = listOf(base.clips[0].copy(mask = masked))), EditAction.SetMask("a", null))
        assertNull(cleared.clips[0].mask)
    }

    @Test
    fun maskAndChroma_workOnOverlays() {
        val masked = EditReducer.reduce(base, EditAction.SetMask("o", ClipMask(MaskShape.HEART)))
        assertEquals(MaskShape.HEART, masked.overlays[0].clip.mask?.shape)
        val keyed = EditReducer.reduce(masked, EditAction.SetChroma("o", ChromaKey(0xFF00FF00.toInt(), intensity = 3f, shadow = -1f)))
        assertEquals(ChromaKey(0xFF00FF00.toInt(), intensity = 1f, shadow = 0f), keyed.overlays[0].clip.chroma)
    }

    @Test
    fun roundShapes_startSquareOnScreen() {
        // A 16:9 picture: the circle's width fraction is smaller so its pixels match its height.
        val circle = ClipMask.of(MaskShape.CIRCLE, 16f / 9f)
        assertEquals(circle.width * 16f / 9f, circle.height, 1e-5f)
        val tall = ClipMask.of(MaskShape.STAR, 9f / 16f)
        assertEquals(tall.width * 9f / 16f, tall.height, 1e-5f)
        assertEquals(ClipMask(MaskShape.LINEAR), ClipMask.of(MaskShape.LINEAR, 2f))
    }

    @Test
    fun gestures_moveScaleTurnAndFeather() {
        val mask = ClipMask(MaskShape.CIRCLE, width = 0.5f, height = 0.5f, rotationDegrees = 170f)
        val moved = MaskGeometry.moved(mask, 0.2f, -2f)
        assertEquals(0.2f, moved.centerX)
        assertEquals(-1f, moved.centerY)
        val turned = MaskGeometry.scaledAndTurned(mask, zoom = 2f, degrees = 20f)
        assertEquals(1f, turned.width)
        assertEquals(-170f, turned.rotationDegrees)
        assertEquals(0f, MaskGeometry.feathered(mask, -0.3f).feather)
        assertEquals(0.4f, MaskGeometry.resized(mask, -0.4f, 0.2f).width)
    }

    @Test
    fun toPicture_turnsWithTheMask() {
        val mask = ClipMask(MaskShape.RECTANGLE, centerX = 0.1f, width = 0.5f, height = 0.5f, rotationDegrees = 90f)
        // The mask's right edge midpoint, turned 90° clockwise, points down on a square picture.
        val (x, y) = mask.toPicture(0.5f, 0f, pictureAspect = 1f)
        assertEquals(0.1f, x, 1e-5f)
        assertEquals(0.25f, y, 1e-5f)
    }

    @Test
    fun chromaThresholds_growWithTheSliders() {
        val (low, lowSoft) = ChromaKey(0, intensity = 0f, shadow = 0f).thresholds()
        val (high, highSoft) = ChromaKey(0, intensity = 1f, shadow = 1f).thresholds()
        assertEquals(true, high > low)
        assertEquals(true, highSoft > lowSoft)
    }
}
