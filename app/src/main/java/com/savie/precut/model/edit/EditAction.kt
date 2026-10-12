package com.savie.precut.model.edit

/** Every change a user can make to a project. Applied by [EditReducer]. */
sealed interface EditAction {
    /** Sets a clip's trim range in source time; values are clamped to a valid range. */
    data class TrimClip(val clipId: String, val trimStartMs: Long, val trimEndMs: Long) : EditAction
}
