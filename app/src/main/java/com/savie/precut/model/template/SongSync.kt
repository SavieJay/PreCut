package com.savie.precut.model.template

import com.savie.precut.model.AudioClip
import com.savie.precut.model.Project
import com.savie.precut.model.clipById
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.edit.beatCutPlan
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.totalDurationMs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Most beats a cut spans when a template is synced to a song. */
const val MAX_BEATS_PER_CUT = 8

/**
 * How many beats each cut spans so the template keeps its own pace on a song at [bpm]: its median
 * slot length over the beat interval, between 1 and [MAX_BEATS_PER_CUT].
 */
fun ProjectTemplate.beatsPerCut(bpm: Float): Int {
    if (bpm <= 0f) return 1
    val lengths = slots.mapNotNull { project.clipById(it.clipId)?.timelineDurationMs() }.sorted()
    if (lengths.isEmpty()) return 1
    return (lengths[lengths.size / 2] / (60_000f / bpm)).roundToInt().coerceIn(1, MAX_BEATS_PER_CUT)
}

/**
 * This project (a filled template) set to [music]: the song from the start with [beatsMs] marked,
 * each main-track clip ending on the next beat (Cut to beat, in points kept), and texts, layers
 * and overlays moved with the clip they sit over, so a title stays on its clip. Beats in the first
 * half step are skipped: the first clip would only flash before the pace starts. Without beats
 * only the song is added. All through the reducer, as normal edits.
 */
fun Project.syncedToSong(music: AudioClip, beatsMs: List<Long>): Project {
    val withMusic = EditReducer.reduce(this, EditAction.AddAudio(music.copy(timelineStartMs = 0L)))
    val usable = beatsMs.withoutEarlyBeats()
    if (usable.isEmpty()) return withMusic
    val marked = EditReducer.reduce(withMusic, EditAction.SetBeats(music.id, usable))
    return EditReducer.reduce(marked, marked.beatCutPlan(music.id).edit).timedLike(this)
}

/** These beats less those within half a step of the start (the step: the median gap between them). */
internal fun List<Long>.withoutEarlyBeats(): List<Long> {
    if (size < 2) return this
    val step = zipWithNext { a, b -> b - a }.sorted()[(size - 1) / 2]
    return dropWhile { it < step / 2 }
}

/**
 * [before]'s texts, layers and overlay starts, moved onto this project's clips: a time inside a
 * clip of [before] lands at the same fraction of the same clip here. The reducer clamps texts to a
 * shorter timeline, so the times come from [before], not from this project.
 */
internal fun Project.timedLike(before: Project): Project {
    if (clips.isEmpty() || clips.size != before.clips.size) return this
    val from = before.clipStartTimesMs() + before.totalDurationMs()
    val to = clipStartTimesMs() + totalDurationMs()
    fun map(t: Long): Long {
        val i = (from.indices.lastOrNull { from[it] <= t } ?: 0).coerceAtMost(from.size - 2)
        val span = from[i + 1] - from[i]
        if (span <= 0) return to[i]
        return to[i] + ((t - from[i]) * (to[i + 1] - to[i]).toDouble() / span).roundToLong()
    }
    val overlayStarts = before.overlays.associate { it.id to map(it.timelineStartMs) }
    return copy(
        textOverlays = before.textOverlays.map { it.copy(startMs = map(it.startMs), endMs = map(it.endMs)) },
        effectLayers = before.effectLayers.map { it.copy(startMs = map(it.startMs), endMs = map(it.endMs)) },
        lookLayers = before.lookLayers.map { it.copy(startMs = map(it.startMs), endMs = map(it.endMs)) },
        overlays = overlays.map { overlay -> overlayStarts[overlay.id]?.let { overlay.copy(timelineStartMs = it) } ?: overlay },
    )
}
