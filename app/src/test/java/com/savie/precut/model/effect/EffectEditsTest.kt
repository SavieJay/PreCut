package com.savie.precut.model.effect

import com.savie.precut.media.compositionSpecOf
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.effectLanes
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EffectEditsTest {
    private val glitch = EffectSettings(EffectType.GLITCH, intensity = 0.8f)
    private val base = project(videoClip("a", sourceDurationMs = 5_000L), videoClip("b", sourceDurationMs = 5_000L))
    private val layer = EffectLayer("e", 1_000L, 4_000L, glitch)

    private fun reduce(vararg actions: EditAction) = actions.fold(base) { p, a -> EditReducer.reduce(p, a) }

    @Test
    fun addAndUpdateLayers_keepThemInTheTimeline() {
        val p = reduce(EditAction.AddEffectLayer(layer.copy(endMs = 60_000L)))
        assertEquals(1_000L to 10_000L, p.effectLayers.single().let { it.startMs to it.endMs })
        val moved = EditReducer.reduce(p, EditAction.UpdateEffectLayer(p.effectLayers.single().copy(startMs = 2_000L, effect = glitch.copy(speed = 9f))))
        assertEquals(2_000L, moved.effectLayers.single().startMs)
        assertEquals(MAX_EFFECT_SPEED, moved.effectLayers.single().effect.speed)
    }

    @Test
    fun splitDuplicateDelete() {
        val split = reduce(EditAction.AddEffectLayer(layer), EditAction.SplitEffectLayer("e", 2_500L, "e2"))
        assertEquals(listOf(1_000L to 2_500L, 2_500L to 4_000L), split.effectLayers.map { it.startMs to it.endMs })
        assertEquals(glitch, split.effectLayers[1].effect)
        // Too close to an edge: nothing happens.
        assertSame(split, EditReducer.reduce(split, EditAction.SplitEffectLayer("e2", 2_550L, "x")))
        val dup = reduce(EditAction.AddEffectLayer(layer), EditAction.DuplicateEffectLayer("e", "d"))
        assertEquals(4_000L to 7_000L, dup.effectLayers[1].let { it.startMs to it.endMs })
        // Overlapping layers stack into rows.
        val stacked = reduce(EditAction.AddEffectLayer(layer), EditAction.AddEffectLayer(layer.copy(id = "f", startMs = 3_000L, endMs = 5_000L)))
        assertEquals(2, stacked.effectLanes().size)
        assertEquals(emptyList<EffectLayer>(), EditReducer.reduce(dup, EditAction.DeleteEffectLayer("e")).effectLayers.filter { it.id == "e" })
    }

    @Test
    fun clipEffects_setRemoveAndApplyToAll() {
        val set = reduce(EditAction.SetClipEffect("a", glitch))
        assertEquals(glitch, set.clips[0].effect)
        assertNull(set.clips[1].effect)
        val all = EditReducer.reduce(set, EditAction.SetEffectOnAllClips(glitch))
        assertEquals(listOf(glitch, glitch), all.clips.map { it.effect })
        assertSame(all, EditReducer.reduce(all, EditAction.SetEffectOnAllClips(glitch)))
        assertNull(EditReducer.reduce(all, EditAction.SetClipEffect("b", null)).clips[1].effect)
    }

    @Test
    fun spec_carriesLayersAndClipEffectsWithTheirStarts() {
        val p = reduce(EditAction.AddEffectLayer(layer), EditAction.SetClipEffect("b", glitch))
        val spec = compositionSpecOf(p)
        assertEquals(listOf(com.savie.precut.media.EffectLayerSpec(1_000_000L, 4_000_000L, glitch)), spec.effectLayers)
        assertNull(spec.items[0].visual.effect)
        // Clip b starts at 5 s, so its effect animates from there.
        assertEquals(com.savie.precut.media.ClipEffectSpec(glitch, 5_000_000L), spec.items[1].visual.effect)
    }
}
