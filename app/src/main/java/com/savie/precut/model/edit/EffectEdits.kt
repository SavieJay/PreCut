package com.savie.precut.model.edit

import com.savie.precut.model.Project
import com.savie.precut.model.effect.EffectLayer
import com.savie.precut.model.totalDurationMs

/** Applies an effect action; the same instance when nothing changed. */
internal fun Project.applyEffectEdit(action: EditAction.EffectEdit): Project = when (action) {
    is EditAction.AddEffectLayer -> copy(effectLayers = effectLayers + action.layer.normalized())
    is EditAction.UpdateEffectLayer -> updateEffectLayer(action.layer.id) { action.layer.normalized() }
    is EditAction.DeleteEffectLayer -> effectLayers.filterNot { it.id == action.layerId }
        .let { if (it.size == effectLayers.size) this else copy(effectLayers = it) }
    is EditAction.SplitEffectLayer -> {
        val index = effectLayers.indexOfFirst { it.id == action.layerId }
        val layer = effectLayers.getOrNull(index)
        if (layer == null || action.atMs - layer.startMs < MIN_CLIP_DURATION_MS || layer.endMs - action.atMs < MIN_CLIP_DURATION_MS) {
            this
        } else {
            val parts = listOf(layer.copy(endMs = action.atMs), layer.copy(id = action.newId, startMs = action.atMs))
            copy(effectLayers = effectLayers.subList(0, index) + parts + effectLayers.subList(index + 1, effectLayers.size))
        }
    }
    is EditAction.DuplicateEffectLayer -> {
        val index = effectLayers.indexOfFirst { it.id == action.layerId }
        val layer = effectLayers.getOrNull(index)
        if (layer == null) {
            this
        } else {
            val length = layer.endMs - layer.startMs
            val copy = layer.copy(id = action.newId, startMs = layer.endMs, endMs = layer.endMs + length)
            copy(effectLayers = effectLayers.toMutableList().also { it.add(index + 1, copy) })
        }
    }
    is EditAction.SetClipEffect -> updateAnyClip(action.clipId) { it.copy(effect = action.effect?.normalized()) }
    is EditAction.SetEffectOnAllClips -> {
        val effect = action.effect?.normalized()
        if (clips.all { it.effect == effect }) this else copy(clips = clips.map { it.copy(effect = effect) })
    }
}

private fun EffectLayer.normalized() = copy(effect = effect.normalized())

private fun Project.updateEffectLayer(id: String, transform: (EffectLayer) -> EffectLayer): Project {
    val index = effectLayers.indexOfFirst { it.id == id }
    if (index < 0) return this
    val updated = transform(effectLayers[index])
    if (updated == effectLayers[index]) return this
    return copy(effectLayers = effectLayers.toMutableList().also { it[index] = updated })
}

/** Keeps every effect layer inside the timeline and at least [MIN_CLIP_DURATION_MS] long (when it allows). */
internal fun Project.withClampedEffectLayers(): Project {
    if (effectLayers.isEmpty()) return this
    val total = totalDurationMs()
    if (total == 0L) return copy(effectLayers = emptyList())
    val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(total)
    val clamped = effectLayers.map { layer ->
        val start = layer.startMs.coerceIn(0L, total - minLength)
        layer.copy(startMs = start, endMs = layer.endMs.coerceIn(start + minLength, total))
    }
    return if (clamped == effectLayers) this else copy(effectLayers = clamped)
}
