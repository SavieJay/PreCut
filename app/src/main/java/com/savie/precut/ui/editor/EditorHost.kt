package com.savie.precut.ui.editor

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
}
