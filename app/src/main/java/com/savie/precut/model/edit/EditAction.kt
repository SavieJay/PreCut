package com.savie.precut.model.edit

import com.savie.precut.model.Clip

/**
 * Every change a user can make to a project. Applied by [EditReducer]. New ids are passed in so
 * the reducer stays pure and deterministic.
 */
sealed interface EditAction {
    /** Inserts [clips] at [atIndex] (clamped), or at the end when null. */
    data class AddClips(val clips: List<Clip>, val atIndex: Int? = null) : EditAction

    data class DeleteClip(val clipId: String) : EditAction

    /** Splits the clip under timeline time [atTimelineMs]; the second half gets [newClipId]. */
    data class SplitClip(val atTimelineMs: Long, val newClipId: String) : EditAction

    /** Moves the clip at index [from] so it ends up at index [to]. */
    data class MoveClip(val from: Int, val to: Int) : EditAction

    /** Inserts a copy right after the original. */
    data class DuplicateClip(val clipId: String, val newClipId: String) : EditAction

    /** Sets a clip's trim range in source time; values are clamped to a valid range. */
    data class TrimClip(val clipId: String, val trimStartMs: Long, val trimEndMs: Long) : EditAction
}
