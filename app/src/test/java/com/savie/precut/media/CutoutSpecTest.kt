package com.savie.precut.media

import com.savie.precut.model.OverlayClip
import com.savie.precut.model.frame.Cutout
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutSpecTest {

    private val first = videoClip("a", sourceDurationMs = 4_000L)
    private val second = videoClip("b", sourceDurationMs = 10_000L, trimStartMs = 2_000L, trimEndMs = 8_000L, speed = 2f)
    private val cutout = Cutout(MATTE, second.uri, 2_000L, 8_000L)

    @Test
    fun mainClip_getsItsMatteTimedFromItsStart() {
        val spec = compositionSpecOf(project(first, second.copy(cutout = cutout)))
        assertNull(spec.items[0].visual.cutout)
        val cut = checkNotNull(spec.items[1].visual.cutout)
        assertEquals(MATTE, cut.matteUri)
        assertEquals(4_000_000L, cut.clipStartUs)
        // 1 s into the clip at 2x is 2 s into the trim: source 4 s.
        assertEquals(4_000_000L, cut.matteUsAt(5_000_000L))
        assertEquals(2_000_000L, cut.matteUsAt(4_000_000L))
        assertEquals(8_000_000L, cut.matteUsAt(7_000_000L))
        // Cut pictures need placing over the canvas, and PreCut's own (SDR) shaders.
        assertNotNull(spec.items[1].visual.frame)
        assertTrue(spec.usesOwnShaders())
    }

    @Test
    fun overlay_isTimedFromWhereItSits() {
        val overlay = videoClip("o").let { it.copy(cutout = Cutout(MATTE, it.uri, 0L, 10_000L)) }
        val spec = compositionSpecOf(project(videoClip("a", sourceDurationMs = 10_000L)).copy(overlays = listOf(OverlayClip(overlay, 3_000L))))
        val cut = checkNotNull(spec.overlayLanes.single().items.single().item.visual.cutout)
        assertEquals(3_000_000L, cut.clipStartUs)
        assertEquals(1_500_000L, cut.matteUsAt(4_500_000L))
    }

    @Test
    fun aMatteOfOtherMedia_showsTheClipUncut() {
        val stale = second.copy(cutout = cutout.copy(sourceUri = "content://media/old"))
        val cut = checkNotNull(compositionSpecOf(project(first, stale)).items[1].visual.cutout)
        assertNull(cut.matteUsAt(5_000_000L))
    }

    @Test
    fun edgeRange_movesWithEdgeAndWidensWithSoftness() {
        fun range(softness: Float, edge: Float) =
            CutoutSpec(MATTE, softness, edge, 0L, second.copy(cutout = cutout)).edgeRange()
        val (low, high) = range(0.3f, 0f)
        assertEquals(0.5f, (low + high) / 2f, 1e-6f)
        val (sharpLow, sharpHigh) = range(0f, 0f)
        assertTrue(sharpHigh - sharpLow < high - low)
        // Looser keeps more: the step moves down the matte.
        assertTrue(range(0.3f, 1f).second < high)
        assertTrue(range(0.3f, -1f).first > low)
        // Always a real step inside 0..1, even at the extremes.
        for (softness in listOf(0f, 1f)) {
            for (edge in listOf(-1f, 0f, 1f)) {
                val (a, b) = range(softness, edge)
                assertTrue(a >= 0f && b <= 1f && b - a >= 0.02f)
            }
        }
    }

    private companion object {
        const val MATTE = "file:///data/mattes/b.matte"
    }
}
