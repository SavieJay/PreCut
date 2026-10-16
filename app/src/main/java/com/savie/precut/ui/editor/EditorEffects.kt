package com.savie.precut.ui.editor

import com.savie.precut.data.EffectFavourites
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.effect.DEFAULT_EFFECT_LAYER_MS
import com.savie.precut.model.effect.EffectLayer
import com.savie.precut.model.effect.EffectSettings
import com.savie.precut.model.effect.EffectType
import com.savie.precut.model.clipById
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Effects panel changes. */
sealed interface EffectTarget {
    /** Nothing yet: a picked effect asks "This clip or Everything" (S-04). */
    data object New : EffectTarget
    data class Clip(val clipId: String) : EffectTarget
    data class Layer(val layerId: String) : EffectTarget
}

/** Video effects (Phase 11): the Effects panel (S-03), its Apply-to choice (S-04) and effect layers. */
class EditorEffects(private val host: EditorHost, private val favouriteStore: EffectFavourites) {

    val favourites: StateFlow<Set<EffectType>> = favouriteStore.favourites

    private val _pending = MutableStateFlow<EffectSettings?>(null)
    /** The effect picked in New mode, waiting for its target. */
    val pending: StateFlow<EffectSettings?> = _pending.asStateFlow()

    /** Opens the panel for the selected clip or effect layer, or to add a new effect. */
    fun open() {
        if (host.ready?.project?.clips.isNullOrEmpty()) return
        host.update { it.copy(activeTool = EditorTool.EFFECTS, selectedTextId = null, selectedAudioId = null, audioMenuOpen = false) }
    }

    /** A main-track clip (overlays are drawn by their own pass, without clip effects) or an effect layer. */
    fun target(ready: EditorUiState.Ready): EffectTarget {
        ready.selectedLayerId?.takeIf { id -> ready.project.effectLayers.any { it.id == id } }?.let { return EffectTarget.Layer(it) }
        ready.selectedClipId?.takeIf { id -> ready.project.clips.any { it.id == id } }?.let { return EffectTarget.Clip(it) }
        return EffectTarget.New
    }

    /** The effect the target has now; null for none (or a new effect). */
    fun current(ready: EditorUiState.Ready): EffectSettings? = when (val target = target(ready)) {
        is EffectTarget.Layer -> ready.project.effectLayers.firstOrNull { it.id == target.layerId }?.effect
        is EffectTarget.Clip -> ready.project.clipById(target.clipId)?.effect
        EffectTarget.New -> null
    }

    fun onPick(type: EffectType) {
        val ready = host.ready ?: return
        val settings = current(ready)?.copy(type = type) ?: EffectSettings(type)
        when (val target = target(ready)) {
            is EffectTarget.Layer -> updateLayer(target.layerId) { it.copy(effect = settings) }
            is EffectTarget.Clip -> host.applyEdit(EditAction.SetClipEffect(target.clipId, settings))
            EffectTarget.New -> {
                _pending.value = settings
                // With no clip under the playhead, a layer is the only choice.
                if (host.targetClipId() == null) onApplyTo(clip = false) else host.update { it.copy(activeTool = EditorTool.EFFECT_TARGET) }
            }
        }
    }

    /** None: removes the clip's effect or the layer. */
    fun onNone() {
        val ready = host.ready ?: return
        when (val target = target(ready)) {
            is EffectTarget.Layer -> {
                host.applyEdit(EditAction.DeleteEffectLayer(target.layerId))
                host.update { it.copy(selectedLayerId = null) }
            }
            is EffectTarget.Clip -> host.applyEdit(EditAction.SetClipEffect(target.clipId, null))
            EffectTarget.New -> Unit
        }
    }

    fun onIntensity(intensity: Float) = change { it.copy(intensity = intensity) }

    fun onSpeed(speed: Float) = change { it.copy(speed = speed) }

    /** The selected clip's effect on every clip. */
    fun onApplyAll() {
        val ready = host.ready ?: return
        val target = target(ready) as? EffectTarget.Clip ?: return
        host.applyEdit(EditAction.SetEffectOnAllClips(ready.project.clipById(target.clipId)?.effect))
    }

    fun onToggleFavourite(type: EffectType) = favouriteStore.toggle(type)

    /** S-04: the pending effect on the clip under the playhead, or on a new layer there. */
    fun onApplyTo(clip: Boolean) {
        val settings = _pending.value ?: return
        _pending.value = null
        val clipId = host.targetClipId()
        if (clip && clipId != null) {
            host.applyEdit(EditAction.SetClipEffect(clipId, settings))
            host.update { it.copy(selectedClipId = clipId, selectedLayerId = null, activeTool = EditorTool.EFFECTS) }
        } else {
            val start = host.positionMs
            val layer = EffectLayer(host.newId(), start, start + DEFAULT_EFFECT_LAYER_MS, settings)
            host.applyEdit(EditAction.AddEffectLayer(layer))
            host.update { it.copy(selectedLayerId = layer.id, selectedClipId = null, activeTool = EditorTool.EFFECTS) }
        }
    }

    /** Back from S-04 to the panel. */
    fun onCancelTarget() {
        _pending.value = null
        host.update { it.copy(activeTool = EditorTool.EFFECTS) }
    }

    // Effect layers on the timeline

    fun onLayerTiming(layerId: String, startMs: Long, endMs: Long) = updateLayer(layerId) { it.copy(startMs = startMs, endMs = endMs) }

    fun onSplitLayer() {
        val id = host.ready?.selectedLayerId ?: return
        val newId = host.newId()
        host.applyEdit(EditAction.SplitEffectLayer(id, host.positionMs, newId))
        if (host.ready?.project?.effectLayers?.any { it.id == newId } == true) host.update { it.copy(selectedLayerId = newId) }
    }

    fun onDuplicateLayer() {
        val id = host.ready?.selectedLayerId ?: return
        val newId = host.newId()
        host.applyEdit(EditAction.DuplicateEffectLayer(id, newId))
        host.update { it.copy(selectedLayerId = newId) }
    }

    fun onDeleteLayer() {
        val id = host.ready?.selectedLayerId ?: return
        host.applyEdit(EditAction.DeleteEffectLayer(id))
        host.update { it.copy(selectedLayerId = null, activeTool = null) }
    }

    private fun change(transform: (EffectSettings) -> EffectSettings) {
        val ready = host.ready ?: return
        val current = current(ready) ?: return
        when (val target = target(ready)) {
            is EffectTarget.Layer -> updateLayer(target.layerId) { it.copy(effect = transform(current)) }
            is EffectTarget.Clip -> host.applyEdit(EditAction.SetClipEffect(target.clipId, transform(current)))
            EffectTarget.New -> Unit
        }
    }

    private fun updateLayer(layerId: String, transform: (EffectLayer) -> EffectLayer) {
        val layer = host.ready?.project?.effectLayers?.firstOrNull { it.id == layerId } ?: return
        host.applyEdit(EditAction.UpdateEffectLayer(transform(layer)))
    }
}
