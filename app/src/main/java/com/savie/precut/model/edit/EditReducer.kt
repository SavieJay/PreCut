package com.savie.precut.model.edit

import com.savie.precut.model.Clip
import com.savie.precut.model.Project

/** Shortest clip a trim may produce, in source time. */
const val MIN_CLIP_DURATION_MS = 100L

object EditReducer {

    /** Returns the project with [action] applied, or the same instance if nothing changed. */
    fun reduce(project: Project, action: EditAction): Project = when (action) {
        is EditAction.TrimClip -> project.updateClip(action.clipId) { it.trimmed(action.trimStartMs, action.trimEndMs) }
    }

    private fun Project.updateClip(clipId: String, transform: (Clip) -> Clip): Project {
        val index = clips.indexOfFirst { it.id == clipId }
        if (index < 0) return this
        val updated = transform(clips[index])
        if (updated == clips[index]) return this
        return copy(clips = clips.toMutableList().also { it[index] = updated })
    }

    private fun Clip.trimmed(requestedStartMs: Long, requestedEndMs: Long): Clip {
        // Clips shorter than the minimum can't be trimmed at all; keep them whole.
        val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(sourceDurationMs)
        val start = requestedStartMs.coerceIn(0L, sourceDurationMs - minLength)
        val end = requestedEndMs.coerceIn(start + minLength, sourceDurationMs)
        return copy(trimStartMs = start, trimEndMs = end)
    }
}
