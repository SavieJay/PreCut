package com.savie.precut.model.edit

import com.savie.precut.model.look.Adjustments
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.look.LookLayer
import com.savie.precut.model.lookLayerLanes
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerEditsTest {

    private val warm = ClipLook(filterId = "basic.warm")
    private val layer = LookLayer("l", 2_000L, 5_000L, warm)
    // 10 s timeline.
    private val base = project(videoClip("a")).copy(lookLayers = listOf(layer))

    private fun reduce(action: EditAction) = EditReducer.reduce(base, action)

    @Test
    fun addUpdateDelete() {
        val added = reduce(EditAction.AddLookLayer(LookLayer("m", 0L, 1_000L)))
        assertEquals(listOf("l", "m"), added.lookLayers.map { it.id })
        val updated = reduce(EditAction.UpdateLookLayer(layer.copy(look = warm.copy(filterIntensity = 2f))))
        assertEquals(1f, updated.lookLayers.single().look.filterIntensity) // clamped
        assertTrue(reduce(EditAction.DeleteLookLayer("l")).lookLayers.isEmpty())
        assertSame(base, reduce(EditAction.DeleteLookLayer("missing")))
    }

    @Test
    fun splitAndDuplicate() {
        val split = reduce(EditAction.SplitLookLayer("l", 3_000L, "l2"))
        assertEquals(listOf(2_000L to 3_000L, 3_000L to 5_000L), split.lookLayers.map { it.startMs to it.endMs })
        assertSame(base, reduce(EditAction.SplitLookLayer("l", 2_050L, "l2")))
        val dup = reduce(EditAction.DuplicateLookLayer("l", "l2"))
        assertEquals(5_000L to 8_000L, dup.lookLayers[1].let { it.startMs to it.endMs })
    }

    @Test
    fun layersAreClampedToTheTimeline_andDroppedWithNoClips() {
        val moved = reduce(EditAction.UpdateLookLayer(layer.copy(startMs = 8_000L, endMs = 14_000L)))
        assertEquals(8_000L to 10_000L, moved.lookLayers.single().let { it.startMs to it.endMs })
        assertTrue(reduce(EditAction.DeleteClip("a")).lookLayers.isEmpty())
    }

    @Test
    fun overlappingLayersStack() {
        val two = reduce(EditAction.AddLookLayer(LookLayer("m", 4_000L, 6_000L)))
        assertEquals(2, two.lookLayerLanes().size)
    }

    @Test
    fun applyAll_filterOnly_orAdjustOnly() {
        val clips = project(videoClip("a").copy(look = ClipLook(filterId = "basic.cool")), videoClip("b"))
        val adjusted = ClipLook(filterId = "basic.warm", adjust = Adjustments(exposure = 20f))
        val adjustAll = EditReducer.reduce(clips, BatchEdits.adjust(clips, BatchEdits.allClipIds(clips), adjusted))
        assertEquals(listOf("basic.cool", null), adjustAll.clips.map { it.look.filterId }) // filters kept
        assertTrue(adjustAll.clips.all { it.look.adjust.exposure == 20f })
        val filterAll = EditReducer.reduce(clips, BatchEdits.filter(clips, BatchEdits.allClipIds(clips), "basic.warm", 0.5f))
        assertTrue(filterAll.clips.all { it.look.filterId == "basic.warm" && it.look.filterIntensity == 0.5f })
    }
}
