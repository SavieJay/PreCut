package com.savie.precut.model.edit

import com.savie.precut.model.Clip
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.ClipType
import com.savie.precut.model.MAX_CLIP_SPEED
import com.savie.precut.model.MAX_VOLUME
import com.savie.precut.model.MIN_CLIP_SPEED
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipAt
import com.savie.precut.model.timelineOffsetToSourceMs
import com.savie.precut.model.totalDurationMs

/** Shortest clip a trim or split may produce, in source time; also the shortest text overlay. */
const val MIN_CLIP_DURATION_MS = 100L

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
                if (it.type == ClipType.IMAGE) it else it.copy(speed = action.speed.coerceIn(MIN_CLIP_SPEED, MAX_CLIP_SPEED))
            }
            is EditAction.SetKeepPitch -> project.updateClip(action.clipId) { it.copy(keepPitch = action.keepPitch) }
            is EditAction.SetClipLook -> project.updateClip(action.clipId) { it.copy(look = action.look.normalized()) }
            is EditAction.LayerEdit -> project.applyLayerEdit(action)
            is EditAction.RotateClip -> project.updateClip(action.clipId) { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
            is EditAction.SetAspectRatio ->
                if (project.aspectRatio == action.aspectRatio) project else project.copy(aspectRatio = action.aspectRatio)
            is EditAction.AddText -> project.copy(textOverlays = project.textOverlays + action.overlay)
            is EditAction.UpdateText -> project.updateText(action.overlay)
            is EditAction.DeleteText -> project.textOverlays.filterNot { it.id == action.overlayId }
                .let { if (it.size == project.textOverlays.size) project else project.copy(textOverlays = it) }
            is EditAction.SplitText -> project.splitText(action)
            is EditAction.DuplicateText -> project.duplicateText(action)
            is EditAction.SetCover ->
                if (project.cover == action.cover && project.coverInExport == action.inExport) {
                    project
                } else {
                    project.copy(cover = action.cover, coverInExport = action.inExport && action.cover != null)
                }
            is EditAction.Batch -> action.actions.fold(project, ::reduce)
            is EditAction.SetClipVolume -> project.updateClip(action.clipId) { it.copy(volume = action.volume.coerceIn(0f, MAX_VOLUME)) }
            is EditAction.SetClipMuted -> project.updateClip(action.clipId) { it.copy(muted = action.muted) }
            is EditAction.ReplaceClip -> project.updateClip(action.clipId) { it.replacedWith(action.uri, action.info) }
            is EditAction.RemoveItems -> {
                val clips = project.clips.filterNot { it.id in action.clipIds }
                val audio = project.audioClips.filterNot { it.id in action.audioIds }
                if (clips.size == project.clips.size && audio.size == project.audioClips.size) {
                    project
                } else {
                    project.copy(clips = clips, audioClips = audio)
                }
            }
            is EditAction.AudioEdit -> project.applyAudioEdit(action)
        }
        return if (edited === project) project else edited.withClampedTexts().withClampedLayers()
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

    private fun Project.splitText(action: EditAction.SplitText): Project {
        val index = textOverlays.indexOfFirst { it.id == action.overlayId }
        if (index < 0) return this
        val text = textOverlays[index]
        if (action.atMs - text.startMs < MIN_CLIP_DURATION_MS || text.endMs - action.atMs < MIN_CLIP_DURATION_MS) return this
        val parts = listOf(text.copy(endMs = action.atMs), text.copy(id = action.newId, startMs = action.atMs))
        return copy(textOverlays = textOverlays.subList(0, index) + parts + textOverlays.subList(index + 1, textOverlays.size))
    }

    private fun Project.duplicateText(action: EditAction.DuplicateText): Project {
        val index = textOverlays.indexOfFirst { it.id == action.overlayId }
        if (index < 0) return this
        val text = textOverlays[index]
        val copy = text.copy(id = action.newId, startMs = text.endMs, endMs = text.endMs + (text.endMs - text.startMs))
        return copy(textOverlays = textOverlays.toMutableList().also { it.add(index + 1, copy) })
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

    private fun Clip.replacedWith(uri: String, info: MediaInfo): Clip {
        val fresh = clipFromMedia(id, uri, info)
        val wantedLength = trimEndMs - trimStartMs
        return fresh.copy(
            trimStartMs = 0L,
            trimEndMs = wantedLength.coerceIn(MIN_CLIP_DURATION_MS.coerceAtMost(fresh.sourceDurationMs), fresh.sourceDurationMs),
            speed = if (fresh.type == ClipType.VIDEO && type == ClipType.VIDEO) speed else 1f,
            keepPitch = keepPitch,
            volume = volume,
            muted = muted,
            look = look,
            rotationDegrees = rotationDegrees,
            fadeInMs = fadeInMs,
            fadeOutMs = fadeOutMs,
        )
    }

    private fun Clip.trimmed(requestedStartMs: Long, requestedEndMs: Long): Clip {
        // Clips shorter than the minimum can't be trimmed at all; keep them whole.
        val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(sourceDurationMs)
        val start = requestedStartMs.coerceIn(0L, sourceDurationMs - minLength)
        val end = requestedEndMs.coerceIn(start + minLength, sourceDurationMs)
        return copy(trimStartMs = start, trimEndMs = end)
    }
}
