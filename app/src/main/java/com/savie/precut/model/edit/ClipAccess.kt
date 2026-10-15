package com.savie.precut.model.edit

import com.savie.precut.model.Clip
import com.savie.precut.model.Project

/**
 * Applies [transform] to the main clip or overlay clip with [clipId], so every clip edit (look,
 * frame, motion, volume, replace…) works on overlays too. Returns the same instance when the clip
 * is missing or unchanged.
 */
internal fun Project.updateAnyClip(clipId: String, transform: (Clip) -> Clip): Project {
    val index = clips.indexOfFirst { it.id == clipId }
    if (index >= 0) {
        val updated = transform(clips[index])
        return if (updated == clips[index]) this else copy(clips = clips.toMutableList().also { it[index] = updated })
    }
    val overlayIndex = overlays.indexOfFirst { it.id == clipId }
    if (overlayIndex < 0) return this
    val overlay = overlays[overlayIndex]
    val updated = transform(overlay.clip)
    return if (updated == overlay.clip) this else copy(overlays = overlays.toMutableList().also { it[overlayIndex] = overlay.copy(clip = updated) })
}
