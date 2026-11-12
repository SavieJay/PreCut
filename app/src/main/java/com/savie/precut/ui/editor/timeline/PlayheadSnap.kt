package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.Project
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.edit.keyframesOnTimeline
import com.savie.precut.model.slotStartTimesMs
import com.savie.precut.model.timelineAt
import com.savie.precut.model.timelineEndMs
import com.savie.precut.model.totalDurationMs

/**
 * Where the playhead snaps while scrubbing (Phase 29D, the 2026 motion spec): every clip's start
 * and the video's end, each audio's marked beats, and the keyframes of the selected clip, overlay
 * or text. Timeline ms, sorted, no duplicates.
 */
fun playheadSnapPoints(project: Project, selection: TimelineSelection): List<Long> = buildSet {
    addAll(project.slotStartTimesMs())
    add(project.totalDurationMs())
    project.audioClips.forEach { audio ->
        val range = audio.timelineStartMs..audio.timelineEndMs()
        audio.beatsMs.map { audio.timelineAt(it) }.filter { it in range }.forEach { add(it) }
    }
    val itemId = selection.clipId ?: selection.layerId
    itemId?.let { id ->
        val clip = project.clips.firstOrNull { it.id == id } ?: project.overlays.firstOrNull { it.id == id }?.clip
        val start = project.clipTimelineRange(id)?.first
        if (clip != null && start != null) clip.keyframesOnTimeline().forEach { add(start + it.atMs) }
    }
    selection.textId?.let { id ->
        project.textOverlays.firstOrNull { it.id == id }?.let { text -> text.keyframes.forEach { add(text.startMs + it.atMs) } }
    }
}.sorted()

/**
 * The time a scrub to [ms] shows: the nearest of [points] within [slopMs], else [ms] itself.
 * More than [slopMs] from every point, scrubbing is unchanged.
 */
fun snapScrub(ms: Long, points: List<Long>, slopMs: Long): Long = TimelineGeometry.snapEdge(ms, points, slopMs) ?: ms
