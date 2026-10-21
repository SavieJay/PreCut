package com.savie.precut.model.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.ClipType
import com.savie.precut.model.MIN_PHOTO_DURATION_MS
import com.savie.precut.model.Project
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.timelineAt
import com.savie.precut.model.timelineEndMs
import kotlin.math.roundToLong

/** Shortest video piece Cut to beat makes; a beat closer than this to a clip's start is skipped. */
const val MIN_BEAT_CUT_MS = 200L

/** What Cut to beat does: [edit] (one undo step), how many clips it [cut], and how many it [leftAlone]. */
data class BeatCutPlan(val edit: EditAction.Batch, val cut: Int, val leftAlone: Int)

/**
 * Cut to beat: each main-track clip, in order, ends on the next marked beat of music clip
 * [audioId] (in timeline time), so every cut lands on the rhythm. Clips keep their in point, or
 * with [fromMiddle] (AutoCut) videos are taken from the middle of their media, skipping shaky
 * starts. A clip with too little media to reach the next beat, or with a speed curve (re-timing a
 * curve isn't reliable), is left as it was. Clips starting after the music's last beat stay as
 * they are. Built from [EditAction.TrimClip]s, so trimming's own rules (fades, animations) apply.
 */
fun Project.beatCutPlan(audioId: String, fromMiddle: Boolean = false): BeatCutPlan {
    val audio = audioClips.firstOrNull { it.id == audioId } ?: return BeatCutPlan(EditAction.Batch(emptyList()), 0, 0)
    val beats = audio.beatsMs.map { audio.timelineAt(it) }.filter { it in 1..audio.timelineEndMs() }.sorted()
    var project = this
    val trims = mutableListOf<EditAction>()
    var leftAlone = 0
    for (index in clips.indices) {
        val clip = project.clips[index]
        val start = project.clipStartTimesMs()[index]
        val shortest = if (clip.type == ClipType.IMAGE) MIN_PHOTO_DURATION_MS else MIN_BEAT_CUT_MS
        val target = beats.firstOrNull { it - start >= shortest } ?: break
        val middle = fromMiddle && clip.type == ClipType.VIDEO
        val from = if (middle) 0L else clip.trimStartMs
        val reachable = ((clip.sourceDurationMs - from) / clip.speed.toDouble()).toLong()
        if (clip.speedCurve != null || target - start > reachable) {
            leftAlone++
            continue
        }
        val length = ((target - start) * clip.speed.toDouble()).roundToLong()
        val trimStart = if (middle) (clip.sourceDurationMs - length) / 2 else clip.trimStartMs
        val trim = EditAction.TrimClip(clip.id, trimStart, (trimStart + length).coerceAtMost(clip.sourceDurationMs))
        project = EditReducer.reduce(project, trim)
        trims += trim
    }
    return BeatCutPlan(EditAction.Batch(trims), trims.size, leftAlone)
}

/**
 * AutoCut: [project]'s clips with [music] added at the start, its [beatsMs] marked, and every clip
 * cut to the beat, taken from the middle of its media. Without beats, the clips stay as they were
 * (the music is still added). All through the reducer, as normal edits.
 */
fun autoCutProject(project: Project, music: AudioClip, beatsMs: List<Long>): Project {
    val withMusic = EditReducer.reduce(project, EditAction.AddAudio(music.copy(timelineStartMs = 0L)))
    if (beatsMs.isEmpty()) return withMusic
    val marked = EditReducer.reduce(withMusic, EditAction.SetBeats(music.id, beatsMs))
    return EditReducer.reduce(marked, marked.beatCutPlan(music.id, fromMiddle = true).edit)
}
