package com.savie.precut.model.edit

import com.savie.precut.model.OverlayClip
import com.savie.precut.model.Project
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineOffsetToSourceMs

internal fun Project.applyOverlayEdit(action: EditAction.OverlayEdit): Project = when (action) {
    is EditAction.AddOverlay -> copy(overlays = overlays + action.overlay.copy(timelineStartMs = action.overlay.timelineStartMs.coerceAtLeast(0L)))
    is EditAction.SetOverlayTiming -> updateOverlay(action.overlayId) { overlay ->
        val clip = overlay.clip
        val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(clip.sourceDurationMs)
        val start = action.trimStartMs.coerceIn(0L, clip.sourceDurationMs - minLength)
        val end = action.trimEndMs.coerceIn(start + minLength, clip.sourceDurationMs)
        OverlayClip(clip.copy(trimStartMs = start, trimEndMs = end), action.timelineStartMs.coerceAtLeast(0L))
    }
    is EditAction.SplitOverlay -> splitOverlay(action)
    is EditAction.DuplicateOverlay -> {
        val overlay = overlays.firstOrNull { it.id == action.overlayId }
        if (overlay == null) this else copy(overlays = overlays + OverlayClip(overlay.clip.copy(id = action.newId), overlay.timelineEndMs))
    }
    is EditAction.DeleteOverlay -> overlays.filterNot { it.id == action.overlayId }.let { if (it.size == overlays.size) this else copy(overlays = it) }
    is EditAction.OverlayToMain -> {
        val overlay = overlays.firstOrNull { it.id == action.overlayId }
        if (overlay == null) {
            this
        } else {
            val index = action.atIndex.coerceIn(0, clips.size)
            copy(clips = clips.subList(0, index) + overlay.clip + clips.subList(index, clips.size), overlays = overlays - overlay)
        }
    }
    is EditAction.ClipToOverlay -> {
        val index = clips.indexOfFirst { it.id == action.clipId }
        // The main track keeps at least one clip: it sets the video's length.
        if (index < 0 || clips.size < 2) {
            this
        } else {
            val start = clipStartTimesMs()[index]
            // A clip's junction transition means nothing on an overlay.
            val overlay = OverlayClip(clips[index].copy(transitionOut = null), start)
            copy(clips = clips.filterIndexed { i, _ -> i != index }, overlays = overlays + overlay)
        }
    }
}

private fun Project.splitOverlay(action: EditAction.SplitOverlay): Project {
    val overlay = overlays.firstOrNull { it.id == action.overlayId } ?: return this
    val offset = action.atMs - overlay.timelineStartMs
    val clip = overlay.clip
    if (offset < MIN_CLIP_DURATION_MS || clip.timelineDurationMs() - offset < MIN_CLIP_DURATION_MS) return this
    val sourceMs = clip.timelineOffsetToSourceMs(offset)
    val (first, second) = clip.splitAt(sourceMs, action.newId)
    val index = overlays.indexOf(overlay)
    val parts = listOf(OverlayClip(first, overlay.timelineStartMs), OverlayClip(second, overlay.timelineStartMs + first.timelineDurationMs()))
    return copy(overlays = overlays.subList(0, index) + parts + overlays.subList(index + 1, overlays.size))
}

private fun Project.updateOverlay(overlayId: String, transform: (OverlayClip) -> OverlayClip): Project {
    val index = overlays.indexOfFirst { it.id == overlayId }
    if (index < 0) return this
    val updated = transform(overlays[index])
    return if (updated == overlays[index]) this else copy(overlays = overlays.toMutableList().also { it[index] = updated })
}
