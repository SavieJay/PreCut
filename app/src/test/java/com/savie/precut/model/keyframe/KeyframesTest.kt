package com.savie.precut.model.keyframe

import com.savie.precut.model.TextOverlay
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.edit.atOffset
import com.savie.precut.model.edit.keyframeAt
import com.savie.precut.model.edit.keyframed
import com.savie.precut.model.edit.keyframesOnTimeline
import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.frame.MaskShape
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframesTest {

    @Test
    fun easingCurves_startAndEndInPlace_andBendTheMiddle() {
        listOf(EasingCurve.LINEAR, EasingCurve.EASE_IN, EasingCurve.EASE_OUT, EasingCurve.EASE_IN_OUT).forEach {
            assertEquals(0f, it.ease(0f), 1e-4f)
            assertEquals(1f, it.ease(1f), 1e-4f)
        }
        assertEquals(0.5f, EasingCurve.LINEAR.ease(0.5f), 1e-3f)
        assertTrue(EasingCurve.EASE_IN.ease(0.3f) < 0.3f)
        assertTrue(EasingCurve.EASE_OUT.ease(0.3f) > 0.3f)
        assertEquals(0.5f, EasingCurve.EASE_IN_OUT.ease(0.5f), 1e-3f)
    }

    @Test
    fun valuesAt_interpolatesAlongTheFirstKeyframesEasing() {
        val frames = listOf(
            ClipKeyframe(0L, ClipTransform(scale = 1f), opacity = 0f, volume = 0f),
            ClipKeyframe(1_000L, ClipTransform(scale = 2f, offsetX = 0.5f), opacity = 1f, volume = 2f),
        )
        val middle = frames.valuesAt(500L)!!
        assertEquals(1.5f, middle.transform.scale, 1e-4f)
        assertEquals(0.25f, middle.transform.offsetX, 1e-4f)
        assertEquals(0.5f, middle.opacity, 1e-4f)
        assertEquals(1f, middle.volume, 1e-4f)
        // Outside the keyframes the nearest one holds.
        assertEquals(0f, frames.valuesAt(-100L)!!.opacity)
        assertEquals(1f, frames.valuesAt(5_000L)!!.opacity)
        val eased = listOf(frames[0].copy(easing = Easing.EASE_IN), frames[1]).valuesAt(500L)!!
        assertTrue(eased.opacity < 0.5f)
        assertNull(emptyList<ClipKeyframe>().valuesAt(0L))
    }

    @Test
    fun masks_moveWhenTheShapeMatches_andSwitchOtherwise() {
        val a = ClipMask(MaskShape.CIRCLE, centerX = 0f)
        val b = ClipMask(MaskShape.CIRCLE, centerX = 0.4f)
        val frames = listOf(ClipKeyframe(0L, mask = a), ClipKeyframe(1_000L, mask = b))
        assertEquals(0.2f, frames.valuesAt(500L)!!.mask!!.centerX, 1e-4f)
        val switched = listOf(ClipKeyframe(0L, mask = a), ClipKeyframe(1_000L, mask = b.copy(shape = MaskShape.STAR)))
        assertEquals(MaskShape.CIRCLE, switched.valuesAt(999L)!!.mask!!.shape)
        assertEquals(MaskShape.STAR, switched.valuesAt(1_000L)!!.mask!!.shape)
    }

    @Test
    fun addKeyframe_snapshotsCurrentValues_atSourceTime() {
        // A 2x clip starting at 1 s on the timeline (after a 1 s clip).
        val p = project(videoClip("first", 1_000L), videoClip("a", 10_000L, trimStartMs = 2_000L, speed = 2f).copy(opacity = 0.7f))
        val keyed = EditReducer.reduce(p, EditAction.AddKeyframe("a", 2_000L))
        val clip = keyed.clips[1]
        // 1 s into the clip at 2x is 2 s into its source, after the 2 s trim.
        assertEquals(listOf(4_000L), clip.keyframes.map { it.atMs })
        assertEquals(0.7f, clip.keyframes[0].opacity)
        assertEquals(listOf(1_000L), clip.keyframesOnTimeline().map { it.atMs })
        // Trimming more off the start keeps the keyframe on the same moment of the media.
        val trimmed = EditReducer.reduce(keyed, EditAction.TrimClip("a", 3_000L, 10_000L)).clips[1]
        assertEquals(listOf(500L), trimmed.keyframesOnTimeline().map { it.atMs })
        val removed = EditReducer.reduce(keyed, EditAction.RemoveKeyframe("a", 2_010L)).clips[1]
        assertTrue(removed.keyframes.isEmpty())
    }

    @Test
    fun editsOnKeyframedClips_landInTheKeyframeAtThePlayhead() {
        val base = project(videoClip("a", 4_000L))
        val keyed = EditReducer.reduce(EditReducer.reduce(base, EditAction.AddKeyframe("a", 0L)), EditAction.AddKeyframe("a", 4_000L))
        val action = keyed.keyframed(EditAction.SetOpacity("a", 0.2f), 4_000L)
        assertTrue(action is EditAction.EditKeyframed)
        val editedProject = EditReducer.reduce(keyed, action)
        val edited = editedProject.clips[0]
        assertEquals(1f, edited.keyframeAt(0L)!!.opacity)
        assertEquals(0.2f, edited.keyframeAt(4_000L)!!.opacity)
        assertEquals(0.6f, edited.atOffset(2_000L).opacity, 1e-4f)
        // A new playhead time makes a new keyframe; the fit stays a plain value.
        val moved = EditReducer.reduce(editedProject, editedProject.keyframed(EditAction.SetClipTransform("a", FrameFit.FILL, ClipTransform(scale = 3f)), 2_000L)).clips[0]
        assertEquals(3, moved.keyframes.size)
        assertEquals(FrameFit.FILL, moved.fit)
        assertEquals(3f, moved.keyframeAt(2_000L)!!.transform.scale)
        // Without keyframes, edits stay plain.
        assertTrue(base.keyframed(EditAction.SetOpacity("a", 0.2f), 0L) is EditAction.SetOpacity)
    }

    @Test
    fun easing_isSetOnTheKeyframeAtThePlayhead() {
        val keyed = EditReducer.reduce(project(videoClip("a")), EditAction.AddKeyframe("a", 1_000L))
        val eased = EditReducer.reduce(keyed, EditAction.SetKeyframeEasing("a", 1_000L, Easing.CUSTOM, EasingCurve(0.1f, 0.9f, 0.2f, 1f)))
        assertEquals(Easing.CUSTOM, eased.clips[0].keyframes[0].easing)
        assertEquals(EasingCurve(0.1f, 0.9f, 0.2f, 1f), eased.clips[0].keyframes[0].curve)
    }

    @Test
    fun textKeyframes_takeOnlyChangedValues() {
        val text = TextOverlay("t", "Hi", startMs = 1_000L, endMs = 5_000L, xNorm = 0.5f, yNorm = 0.5f, opacity = 1f)
        val p = project(videoClip("a")).copy(textOverlays = listOf(text))
        val keyed = EditReducer.reduce(EditReducer.reduce(p, EditAction.AddTextKeyframe("t", 1_000L)), EditAction.AddTextKeyframe("t", 5_000L))
        // Move the text at the end; its colour changes too (not keyframed).
        val stored = keyed.textOverlays[0]
        val moved = stored.copy(xNorm = 0.9f, colorArgb = 0xFFFF0000.toInt())
        val edited = EditReducer.reduce(keyed, keyed.keyframed(EditAction.UpdateText(moved), 5_000L)).textOverlays[0]
        assertEquals(0xFFFF0000.toInt(), edited.colorArgb)
        assertEquals(listOf(0.5f, 0.9f), edited.keyframes.map { it.xNorm })
        assertEquals(listOf(1f, 1f), edited.keyframes.map { it.opacity })
        assertEquals(0.7f, edited.atOffset(2_000L).xNorm, 1e-4f)
    }
}
