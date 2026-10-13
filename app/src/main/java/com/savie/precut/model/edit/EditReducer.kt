package com.savie.precut.model.edit

import com.savie.precut.model.Clip
import com.savie.precut.model.Project
import com.savie.precut.model.clipAt
import com.savie.precut.model.timelineOffsetToSourceMs

/** Shortest clip a trim or split may produce, in source time. */
const val MIN_CLIP_DURATION_MS = 100L

object EditReducer {

    /** Returns the project with [action] applied, or the same instance if nothing changed. */
    fun reduce(project: Project, action: EditAction): Project = when (action) {
        is EditAction.AddClips -> project.addClips(action)
        is EditAction.DeleteClip -> project.withClips(project.clips.filterNot { it.id == action.clipId })
        is EditAction.SplitClip -> project.split(action)
        is EditAction.MoveClip -> project.move(action.from, action.to)
        is EditAction.DuplicateClip -> project.duplicate(action)
        is EditAction.TrimClip -> project.updateClip(action.clipId) { it.trimmed(action.trimStartMs, action.trimEndMs) }
    }

    /** True if [reduce] would split something at [atTimelineMs]. Used to enable the Split button. */
    fun canSplit(project: Project, atTimelineMs: Long): Boolean = splitPoint(project, atTimelineMs) != null

    private fun Project.addClips(action: EditAction.AddClips): Project {
        if (action.clips.isEmpty()) return this
        val index = (action.atIndex ?: clips.size).coerceIn(0, clips.size)
        return copy(clips = clips.subList(0, index) + action.clips + clips.subList(index, clips.size))
    }

    private fun Project.split(action: EditAction.SplitClip): Project {
        val (index, sourceMs) = splitPoint(this, action.atTimelineMs) ?: return this
        val clip = clips[index]
        val first = clip.copy(trimEndMs = sourceMs)
        val second = clip.copy(id = action.newClipId, trimStartMs = sourceMs)
        return copy(clips = clips.subList(0, index) + listOf(first, second) + clips.subList(index + 1, clips.size))
    }

    /** Index of the clip to split and the split time in its source, or null if too close to an edge. */
    private fun splitPoint(project: Project, atTimelineMs: Long): Pair<Int, Long>? {
        val position = project.clipAt(atTimelineMs) ?: return null
        val clip = project.clips[position.index]
        val sourceMs = clip.timelineOffsetToSourceMs(position.offsetMs)
        val tooClose = sourceMs - clip.trimStartMs < MIN_CLIP_DURATION_MS ||
            clip.trimEndMs - sourceMs < MIN_CLIP_DURATION_MS
        return if (tooClose) null else position.index to sourceMs
    }

    private fun Project.move(from: Int, to: Int): Project {
        if (from !in clips.indices || to !in clips.indices || from == to) return this
        val mutable = clips.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        return copy(clips = mutable)
    }

    private fun Project.duplicate(action: EditAction.DuplicateClip): Project {
        val index = clips.indexOfFirst { it.id == action.clipId }
        if (index < 0) return this
        return copy(clips = clips.toMutableList().also { it.add(index + 1, clips[index].copy(id = action.newClipId)) })
    }

    private fun Project.withClips(newClips: List<Clip>): Project =
        if (newClips.size == clips.size) this else copy(clips = newClips)

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
