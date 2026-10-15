package com.savie.precut.model.edit

import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.motion.ComboType
import com.savie.precut.model.motion.MAX_COMBO_SPEED
import com.savie.precut.model.motion.MIN_TRANSITION_MS
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.project
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MotionEditsTest {

    private val flash = ClipTransition(TransitionType.FLASH, 600L)
    private val fadeIn = ClipAnimation(AnimationType.FADE, 500L)
    private val slideOut = ClipAnimation(AnimationType.SLIDE_UP, 500L)
    // Two 10 s clips.
    private val base = project(
        videoClip("a").copy(transitionOut = flash, animationIn = fadeIn, animationOut = slideOut),
        videoClip("b"),
    )

    private fun reduce(action: EditAction) = EditReducer.reduce(base, action)

    @Test
    fun transitions_setClampAndApplyToAll() {
        assertEquals(MIN_TRANSITION_MS, reduce(EditAction.SetTransition("b", flash.copy(durationMs = 1L))).clips[1].transitionOut?.durationMs)
        assertNull(reduce(EditAction.SetTransition("a", null)).clips[0].transitionOut)
        val spin = ClipTransition(TransitionType.SPIN)
        val all = reduce(EditAction.SetAllTransitions(spin))
        assertEquals(spin, all.clips[0].transitionOut)
        assertNull(all.clips[1].transitionOut) // the last clip has no junction after it
    }

    @Test
    fun animations_comboClearsInAndOut() {
        val combo = reduce(EditAction.SetAnimations("a", fadeIn, slideOut, ComboAnimation(ComboType.ROCK, speed = 9f))).clips[0]
        assertNull(combo.animationIn)
        assertNull(combo.animationOut)
        assertEquals(MAX_COMBO_SPEED, combo.animationCombo?.speed)
        val plain = reduce(EditAction.SetAnimations("a", null, null, null)).clips[0]
        assertNull(plain.animationIn)
    }

    @Test
    fun split_keepsInFirst_movesOutAndTransitionToSecond() {
        val split = reduce(EditAction.SplitClip(4_000L, "a2")).clips
        assertEquals(fadeIn, split[0].animationIn)
        assertNull(split[0].animationOut)
        assertNull(split[0].transitionOut)
        assertNull(split[1].animationIn)
        assertEquals(slideOut, split[1].animationOut)
        assertEquals(flash, split[1].transitionOut)
    }

    @Test
    fun freeze_splitsAndInsertsTheStillWithTheClipsLook() {
        val framed = base.copy(clips = listOf(base.clips[0].copy(canvas = ClipCanvas(kind = CanvasKind.BLUR)), base.clips[1]))
        val still = clipFromMedia("f", "file:///freezes/f.jpg", MediaInfo(ClipType.IMAGE, 0L, 1080, 1920, 0)).copy(trimEndMs = 3_000L)
        val frozen = EditReducer.reduce(framed, EditAction.FreezeFrame(4_000L, "a2", still))
        assertEquals(listOf("a", "f", "a2", "b"), frozen.clips.map { it.id })
        assertEquals(4_000L, frozen.clips[0].trimEndMs)
        assertEquals(4_000L, frozen.clips[2].trimStartMs)
        assertEquals(CanvasKind.BLUR, frozen.clips[1].canvas.kind)
        assertNull(frozen.clips[1].animationIn)
        assertEquals(framed.totalDurationMs() + 3_000L, frozen.totalDurationMs())
        assertEquals(flash, frozen.clips[2].transitionOut)
    }

    @Test
    fun freeze_nearAnEdge_insertsWithoutSplitting() {
        val still = clipFromMedia("f", "file:///f.jpg", MediaInfo(ClipType.IMAGE, 0L, 10, 10, 0)).copy(trimEndMs = 3_000L)
        assertEquals(listOf("f", "a", "b"), reduce(EditAction.FreezeFrame(20L, "x", still)).clips.map { it.id })
        val atEnd = reduce(EditAction.FreezeFrame(9_990L, "x", still)).clips
        assertEquals(listOf("a", "f", "b"), atEnd.map { it.id })
        assertEquals(flash, atEnd[1].transitionOut) // the still carries the junction on
        assertNull(atEnd[0].transitionOut)
        assertSame(base, reduce(EditAction.FreezeFrame(99_000L, "x", still)))
    }

    @Test
    fun replace_keepsFrameAndMotion() {
        val styled = base.copy(clips = listOf(base.clips[0].copy(opacity = 0.5f, mirrored = true), base.clips[1]))
        val replaced = EditReducer.reduce(styled, EditAction.ReplaceClip("a", "content://new", MediaInfo(ClipType.VIDEO, 8_000L, 1920, 1080, 0))).clips[0]
        assertEquals(0.5f, replaced.opacity)
        assertEquals(true, replaced.mirrored)
        assertEquals(flash, replaced.transitionOut)
        assertEquals(fadeIn, replaced.animationIn)
    }
}
