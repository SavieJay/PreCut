package com.savie.precut.model.edit

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipAt
import com.savie.precut.model.timelineOffsetToSourceMs
import com.savie.precut.model.totalDurationMs

/** Shortest clip a trim or split may produce, in source time; also the shortest text overlay. */
const val MIN_CLIP_DURATION_MS = 100L
const val MIN_SPEED = 0.25f
const val MAX_SPEED = 4f

object EditReducer {

    /** Returns the project with [action] applied, or the same instance if nothing changed. */
    fun reduce(project: Project, action: EditAction): Project {
        val edited = when (action) {
            is EditAction.AddClips -> project.addClips(action)
            is EditAction.DeleteClip -> project.withClips(project.clips.filterNot { it.id == action.clipId })
            is EditAction.SplitClip -> project.split(action)
            is EditAction.MoveClip -> project.move(action.from, action.to)
            is EditAction.DuplicateClip -> project.duplicate(action)
            is EditAction.TrimClip -> project.updateClip(action.clipId) { it.trimmed(action.trimStartMs, action.trimEndMs) }
            is EditAction.SetSpeed -> project.updateClip(action.clipId) {
                if (it.type == ClipType.IMAGE) it else it.copy(speed = action.speed.coerceIn(MIN_SPEED, MAX_SPEED))
            }
            is EditAction.SetFilter -> project.updateClip(action.clipId) { it.copy(filter = action.filter) }
            is EditAction.RotateClip -> project.updateClip(action.clipId) { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
            is EditAction.SetAspectRatio ->
                if (project.aspectRatio == action.aspectRatio) project else project.copy(aspectRatio = action.aspectRatio)
            is EditAction.AddText -> project.copy(textOverlays = project.textOverlays + action.overlay)
            is EditAction.UpdateText -> project.updateText(action.overlay)
            is EditAction.DeleteText -> project.textOverlays.filterNot { it.id == action.overlayId }
                .let { if (it.size == project.textOverlays.size) project else project.copy(textOverlays = it) }
        }
        return if (edited === project) project else edited.withClampedTexts()
    }

    /** True if [reduce] would split something at [atTimelineMs]. Used to enable the Split button. */
    fun canSplit(project: Project, atTimelineMs: Long): Boolean = splitPoint(project, atTimelineMs) != null

    /**
     * Keeps every text overlay inside the timeline, at least [MIN_CLIP_DURATION_MS] long (when the
     * timeline allows) and positioned within the frame. With no clips, overlays are dropped.
     */
    private fun Project.withClampedTexts(): Project {
        if (textOverlays.isEmpty()) return this
        val total = totalDurationMs()
        if (total == 0L) return copy(textOverlays = emptyList())
        val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(total)
        val clamped = textOverlays.map { overlay ->
            val start = overlay.startMs.coerceIn(0L, total - minLength)
            val end = overlay.endMs.coerceIn(start + minLength, total)
            overlay.copy(
                startMs = start,
                endMs = end,
                xNorm = overlay.xNorm.coerceIn(0f, 1f),
                yNorm = overlay.yNorm.coerceIn(0f, 1f),
            )
        }
        return if (clamped == textOverlays) this else copy(textOverlays = clamped)
    }

    private fun Project.updateText(overlay: TextOverlay): Project {
        val index = textOverlays.indexOfFirst { it.id == overlay.id }
        if (index < 0 || textOverlays[index] == overlay) return this
        return copy(textOverlays = textOverlays.toMutableList().also { it[index] = overlay })
    }

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
