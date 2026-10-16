package com.savie.precut.model

import com.savie.precut.media.CurveSpeedProvider
import com.savie.precut.media.ItemSpec
import com.savie.precut.media.compositionSpecOf
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.speed.SpeedSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlapTimelineTest {

    private fun dissolve(ms: Long) = ClipTransition(TransitionType.DISSOLVE, ms)

    // a 4 s, then b 6 s overlapping a by 1 s, then c 2 s with an edge transition (no overlap).
    private val p = project(
        videoClip("a", 4_000L).copy(transitionOut = dissolve(1_000L)),
        videoClip("b", 6_000L).copy(transitionOut = ClipTransition(TransitionType.FADE_BLACK, 500L)),
        videoClip("c", 2_000L),
    )

    @Test
    fun overlaps_startClipsEarly_andShortenTheVideo() {
        assertEquals(listOf(0L, 1_000L, 0L), p.overlapsBeforeMs())
        assertEquals(listOf(0L, 3_000L, 9_000L), p.clipStartTimesMs())
        assertEquals(listOf(0L, 4_000L, 9_000L), p.slotStartTimesMs())
        assertEquals(11_000L, p.totalDurationMs())
    }

    @Test
    fun clipAt_givesTheOverlapToTheEndingClip() {
        assertEquals(ClipPosition(0, 3_500L), p.clipAt(3_500L))
        // Just after a's end: b, 1 s (its hidden head) + 0 s into its own stretch.
        assertEquals(ClipPosition(1, 1_000L), p.clipAt(4_000L))
        assertEquals(ClipPosition(2, 2_000L), p.clipAt(11_000L))
        assertNull(p.clipAt(11_001L))
    }

    @Test
    fun overlap_isAtMostHalfTheShorterClip() {
        val q = project(videoClip("a", 1_000L).copy(transitionOut = dissolve(2_000L)), videoClip("b", 6_000L))
        assertEquals(500L, q.overlapsBeforeMs()[1])
    }

    @Test
    fun spec_cutsTheIncomingHead_andDrawsItInATransitionLayer() {
        val spec = compositionSpecOf(p)
        val b = spec.items[1] as ItemSpec.Video
        assertEquals(1_000L, b.clipStartMs)
        assertEquals(1_000L, b.sound.keyframeOffsetMs)
        val layer = spec.transitions.single()
        assertEquals(TransitionType.DISSOLVE, layer.type)
        assertEquals(3_000_000L to 4_000_000L, layer.startUs to layer.endUs)
        // The layer has the whole clip, drawn as a frame (not transparent).
        assertEquals(0L, (layer.item as ItemSpec.Video).clipStartMs)
        assertEquals(false, layer.item.visual.frame?.transparent)
        // The head's sound fades in across the overlap.
        val head = spec.transitionAudio!!.items.single()
        assertEquals(3_000_000L, head.gapBeforeUs)
        assertEquals(1_000L, (head.item as ItemSpec.Video).clipEndMs)
        assertEquals(1_000L, head.item.sound.fadeInMs)
        // The edge transition between b and c is still on their edges.
        assertEquals(TransitionType.FADE_BLACK, spec.items[2].visual.transition?.head)
        assertNull(spec.items[0].visual.transition)
    }

    @Test
    fun curveOffset_shiftsTheSteps() {
        val steps = SpeedSteps(4_000_000L, floatArrayOf(1f, 2f, 3f, 4f))
        val provider = CurveSpeedProvider(steps, offsetUs = 1_500_000L)
        assertEquals(2f, provider.getSpeed(0L))
        // Next change: the source step at 2 s, which is 0.5 s into the shifted item.
        assertEquals(500_000L, provider.getNextSpeedChangeTimeUs(0L))
    }
}
