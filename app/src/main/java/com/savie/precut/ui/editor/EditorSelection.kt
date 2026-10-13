package com.savie.precut.ui.editor

import com.savie.precut.model.FilterType
import com.savie.precut.model.Project
import com.savie.precut.model.edit.BatchEdits
import com.savie.precut.model.edit.EditAction

/**
 * Multi-select mode: taps toggle clips, texts and audio clips, and the selection toolbar applies
 * one edit to all of them as a single undo step.
 */
class EditorSelection(private val host: EditorHost) {

    val isActive: Boolean get() = host.ready?.multiSelect != null

    /** Starts select mode, keeping what's currently selected. */
    fun onStart() = host.update {
        it.copy(
            multiSelect = setOfNotNull(it.selectedClipId, it.selectedTextId, it.selectedAudioId),
            selectedClipId = null,
            selectedTextId = null,
            selectedAudioId = null,
            activeTool = null,
            audioMenuOpen = false,
        )
    }

    fun onDone() = host.update { it.copy(multiSelect = null, activeTool = null) }

    fun toggle(id: String) = host.update { ready ->
        val selected = ready.multiSelect ?: return@update ready
        ready.copy(multiSelect = if (id in selected) selected - id else selected + id)
    }

    fun onDelete() {
        apply { project, ids -> BatchEdits.delete(project, ids) }
        host.update { it.copy(multiSelect = emptySet(), activeTool = null) }
    }

    fun onDuplicate() = apply { project, ids -> BatchEdits.duplicate(project, ids, host::newId) }

    fun onSpeed(speed: Float) = apply { project, ids -> BatchEdits.speed(project, ids, speed) }

    fun onFilter(filter: FilterType) = apply { project, ids -> BatchEdits.filter(project, ids, filter) }

    fun onVolume(volume: Float) = apply { project, ids -> BatchEdits.volume(project, ids, volume) }

    private fun apply(build: (Project, Set<String>) -> EditAction.Batch) {
        val ready = host.ready ?: return
        val ids = ready.multiSelect?.takeIf { it.isNotEmpty() } ?: return
        host.applyEdit(build(ready.project, ids))
    }
}
