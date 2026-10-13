package com.savie.precut.model.edit

import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.model.look.ClipLook

/**
 * Builds one [EditAction.Batch] (one undo step) acting on several items, given by id across clips,
 * texts and audio clips. Ids that don't apply to an edit are skipped.
 */
object BatchEdits {

    fun delete(project: Project, ids: Set<String>): EditAction.Batch = EditAction.Batch(
        listOf(
            EditAction.RemoveItems(
                clipIds = project.clips.map { it.id }.filter { it in ids }.toSet(),
                audioIds = project.audioClips.map { it.id }.filter { it in ids }.toSet(),
            ),
        ) + project.textOverlays.filter { it.id in ids }.map { EditAction.DeleteText(it.id) },
    )

    /** Each selected item gets a copy right after it. */
    fun duplicate(project: Project, ids: Set<String>, newId: () -> String): EditAction.Batch = EditAction.Batch(
        project.clips.filter { it.id in ids }.map { EditAction.DuplicateClip(it.id, newId()) } +
            project.textOverlays.filter { it.id in ids }.map { EditAction.DuplicateText(it.id, newId()) } +
            project.audioClips.filter { it.id in ids }.map { EditAction.DuplicateAudio(it.id, newId()) },
    )

    /** Video clips only; photos have no speed. */
    fun speed(project: Project, ids: Set<String>, speed: Float): EditAction.Batch = EditAction.Batch(
        project.clips.filter { it.id in ids && it.type == ClipType.VIDEO }.map { EditAction.SetSpeed(it.id, speed) },
    )

    /** Sets the filter (and its intensity) on each clip, keeping their other look settings. */
    fun filter(project: Project, ids: Set<String>, filterId: String?, intensity: Float): EditAction.Batch = EditAction.Batch(
        project.clips.filter { it.id in ids }.map {
            EditAction.SetClipLook(it.id, it.look.copy(filterId = filterId, filterIntensity = intensity))
        },
    )

    /** Adjust, HSL and Curves on each clip, keeping their filters. */
    fun adjust(project: Project, ids: Set<String>, from: ClipLook): EditAction.Batch = EditAction.Batch(
        project.clips.filter { it.id in ids }.map {
            EditAction.SetClipLook(it.id, it.look.copy(adjust = from.adjust, hsl = from.hsl, curves = from.curves))
        },
    )

    /** The whole look (filter, Adjust, HSL, Curves) on each clip. */
    fun look(project: Project, ids: Set<String>, look: ClipLook): EditAction.Batch = EditAction.Batch(
        project.clips.filter { it.id in ids }.map { EditAction.SetClipLook(it.id, look) },
    )

    /** Clips' own sound and audio clips. */
    fun volume(project: Project, ids: Set<String>, volume: Float): EditAction.Batch = EditAction.Batch(
        project.clips.filter { it.id in ids }.map { EditAction.SetClipVolume(it.id, volume) } +
            project.audioClips.filter { it.id in ids }.map { EditAction.SetAudioVolume(it.id, volume) },
    )

    /** Every clip on the main track ("Apply to all"). */
    fun allClipIds(project: Project): Set<String> = project.clips.map { it.id }.toSet()
}
