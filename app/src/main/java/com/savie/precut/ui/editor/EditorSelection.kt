package com.savie.precut.ui.editor

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
            selectedLayerId = null,
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

    fun onFilter(filterId: String?) = apply { project, ids -> BatchEdits.filter(project, ids, filterId, intensity = 1f) }

    /** The filter strength on every picked clip, keeping their filters. */
    fun onFilterIntensity(intensity: Float) = apply { project, ids ->
        EditAction.Batch(
            project.clips.filter { it.id in ids }.map { EditAction.SetClipLook(it.id, it.look.copy(filterIntensity = intensity)) },
        )
    }

    fun onVolume(volume: Float) = apply { project, ids -> BatchEdits.volume(project, ids, volume) }

    private fun apply(build: (Project, Set<String>) -> EditAction.Batch) {
        val ready = host.ready ?: return
        val ids = ready.multiSelect?.takeIf { it.isNotEmpty() } ?: return
        host.applyEdit(build(ready.project, ids))
    }
}
