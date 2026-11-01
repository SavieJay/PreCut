package com.savie.precut.ui.editor

import com.savie.precut.model.Project
import com.savie.precut.model.edit.EditAction
import kotlinx.coroutines.CoroutineScope

/** What the editor's feature controllers ([EditorAudio], [EditorSelection], [EditorCover]) need from the ViewModel. */
interface EditorHost {
    val ready: EditorUiState.Ready?
    val positionMs: Long
    val scope: CoroutineScope
    fun applyEdit(action: EditAction)
    fun update(transform: (EditorUiState.Ready) -> EditorUiState.Ready)
    fun send(event: EditorEvent)
    fun newId(): String
    fun targetClipId(): String?
    fun seekTo(positionMs: Long)

    /** Rebuilds the preview from the current state now (e.g. a before/after peek). */
    fun refreshPreview()

    /** The preview plays [project] instead of the real one (an AI edit's proposal), or the real one again with null. */
    fun showInstead(project: Project?)
}
