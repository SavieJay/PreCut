package com.savie.precut.model.edit

import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.frame.CanvasPattern
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.CropRatio
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.frame.MIN_CLIP_SCALE
import com.savie.precut.model.frame.contentAspect
import com.savie.precut.model.frame.uprightAspect
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameEditsTest {

    private val strip = CropRect(0f, 0f, 0.5f, 0.25f)
    private val clip = videoClip("a").copy(sourceWidth = 1920, sourceHeight = 1080, crop = strip, cropRatio = CropRatio.RATIO_9_16)
    private val base = project(clip, videoClip("b"))

    private fun reduce(action: EditAction) = EditReducer.reduce(base, action)
    private fun first(action: EditAction) = reduce(action).clips.first()

    @Test
    fun rotate_turnsTheCropAndRatio() {
        val rotated = first(EditAction.RotateClip("a"))
        assertEquals(90, rotated.rotationDegrees)
        assertEquals(CropRect(0.75f, 0f, 1f, 0.5f), rotated.crop)
        assertEquals(CropRatio.RATIO_16_9, rotated.cropRatio)
        assertEquals(1080f / 1920f, rotated.uprightAspect(), 1e-4f)
    }

    @Test
    fun mirror_isLeftRightOnScreen_evenWhenSideways() {
        val flat = first(EditAction.ToggleMirror("a"))
        assertTrue(flat.mirrored)
        assertEquals(0, flat.rotationDegrees)
        assertEquals(CropRect(0.5f, 0f, 1f, 0.25f), flat.crop)
        val sideways = EditReducer.reduce(base, EditAction.RotateClip("a")).let { EditReducer.reduce(it, EditAction.ToggleMirror("a")) }.clips.first()
        assertTrue(sideways.mirrored)
        assertEquals(270, sideways.rotationDegrees)
    }

    @Test
    fun setCrop_normalizes_andAFullCropIsNone() {
        val set = first(EditAction.SetCrop("a", CropRect(-1f, 0.2f, 0.5f, 0.21f), CropRatio.FREE, mirrored = true, rotationDegrees = -90))
        assertEquals(CropRect(0f, 0.2f, 0.5f, 0.3f), set.crop?.let { CropRect(it.left, it.top, it.right, Math.round(it.bottom * 1000) / 1000f) })
        assertEquals(270, set.rotationDegrees)
        assertTrue(set.mirrored)
        assertNull(first(EditAction.SetCrop("a", CropRect.FULL, CropRatio.FREE, false, 0)).crop)
        assertEquals((1920f / 1080f) * 0.5f / 0.25f, clip.contentAspect(), 1e-4f)
    }

    @Test
    fun transformAndOpacity_areClamped() {
        val t = first(EditAction.SetClipTransform("a", FrameFit.FILL, ClipTransform(scale = 0f, rotationDegrees = 450f)))
        assertEquals(FrameFit.FILL, t.fit)
        assertEquals(MIN_CLIP_SCALE, t.transform.scale)
        assertEquals(90f, t.transform.rotationDegrees)
        assertEquals(0f, first(EditAction.SetOpacity("a", -2f)).opacity)
        assertEquals(1f, first(EditAction.SetOpacity("a", 3f)).opacity)
    }

    @Test
    fun canvas_isNormalized_andAppliesToAll() {
        assertEquals(4, first(EditAction.SetCanvas("a", ClipCanvas(kind = CanvasKind.BLUR, blur = 9))).canvas.blur)
        val photo = first(EditAction.SetCanvas("a", ClipCanvas(kind = CanvasKind.IMAGE, imageUri = "file:///x.jpg", pattern = CanvasPattern.DOTS)))
        assertNull(photo.canvas.pattern)
        val blur = ClipCanvas(kind = CanvasKind.BLUR, blur = 3)
        val all = EditReducer.reduce(base, BatchEdits.canvas(base, blur))
        assertTrue(all.clips.all { it.canvas == blur })
    }

    @Test
    fun unknownClip_changesNothing() {
        assertSame(base, reduce(EditAction.SetOpacity("missing", 0.5f)))
        assertSame(base, reduce(EditAction.SetOpacity("a", 1f)))
        assertTrue(ClipCanvas().isDefault)
    }
}
