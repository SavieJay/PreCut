package com.savie.precut.model.edit

import com.savie.precut.model.Project
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.look.LookLayer
import com.savie.precut.model.totalDurationMs

/** Applies a look-layer action; the same instance when nothing changed. */
internal fun Project.applyLayerEdit(action: EditAction.LayerEdit): Project = when (action) {
    is EditAction.AddLookLayer -> copy(lookLayers = lookLayers + action.layer.copy(look = action.layer.look.normalized()))
    is EditAction.UpdateLookLayer -> updateLayer(action.layer.id) { action.layer.copy(look = action.layer.look.normalized()) }
    is EditAction.DeleteLookLayer -> lookLayers.filterNot { it.id == action.layerId }
        .let { if (it.size == lookLayers.size) this else copy(lookLayers = it) }
    is EditAction.SplitLookLayer -> {
        val index = lookLayers.indexOfFirst { it.id == action.layerId }
        val layer = lookLayers.getOrNull(index)
        if (layer == null || action.atMs - layer.startMs < MIN_CLIP_DURATION_MS || layer.endMs - action.atMs < MIN_CLIP_DURATION_MS) {
            this
        } else {
            val parts = listOf(layer.copy(endMs = action.atMs), layer.copy(id = action.newId, startMs = action.atMs))
            copy(lookLayers = lookLayers.subList(0, index) + parts + lookLayers.subList(index + 1, lookLayers.size))
        }
    }
    is EditAction.DuplicateLookLayer -> {
        val index = lookLayers.indexOfFirst { it.id == action.layerId }
        val layer = lookLayers.getOrNull(index)
        if (layer == null) {
            this
        } else {
            val length = layer.endMs - layer.startMs
            val copy = layer.copy(id = action.newId, startMs = layer.endMs, endMs = layer.endMs + length)
            copy(lookLayers = lookLayers.toMutableList().also { it.add(index + 1, copy) })
        }
    }
}

private fun Project.updateLayer(id: String, transform: (LookLayer) -> LookLayer): Project {
    val index = lookLayers.indexOfFirst { it.id == id }
    if (index < 0) return this
    val updated = transform(lookLayers[index])
    if (updated == lookLayers[index]) return this
    return copy(lookLayers = lookLayers.toMutableList().also { it[index] = updated })
}

/**
 * Keeps every look layer inside the timeline and at least [MIN_CLIP_DURATION_MS] long (when the
 * timeline allows). With no clips, layers are dropped.
 */
internal fun Project.withClampedLayers(): Project {
    if (lookLayers.isEmpty()) return this
    val total = totalDurationMs()
    if (total == 0L) return copy(lookLayers = emptyList())
    val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(total)
    val clamped = lookLayers.map { layer ->
        val start = layer.startMs.coerceIn(0L, total - minLength)
        layer.copy(startMs = start, endMs = layer.endMs.coerceIn(start + minLength, total))
    }
    return if (clamped == lookLayers) this else copy(lookLayers = clamped)
}

/** Clamps values to their ranges and keeps exactly one HSL band per colour. */
internal fun ClipLook.normalized(): ClipLook {
    val bands = if (hsl.size == ClipLook.DEFAULT_HSL.size) hsl else ClipLook.DEFAULT_HSL
    return copy(filterIntensity = filterIntensity.coerceIn(0f, 1f), hsl = bands)
}
