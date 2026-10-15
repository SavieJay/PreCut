package com.savie.precut.ui.editor

import com.savie.precut.media.MediaImporter
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.insertIndexAt
import com.savie.precut.model.overlayById
import com.savie.precut.model.overlayFromMedia
import kotlinx.coroutines.launch

/**
 * Overlays (P-01, P-02): add from the gallery at the playhead, move/trim from the timeline, split,
 * duplicate, delete, and switch between the main track and an overlay. A selected overlay is the
 * editor's selected clip, so the clip tools (Animation, Opacity, Filters, Adjust…) work on it.
 */
class EditorOverlay(private val host: EditorHost, private val importer: MediaImporter) {

    fun onAdd() = host.send(EditorEvent.PickOverlay)

    fun onPicked(uri: String?) {
        if (uri == null) return
        host.scope.launch {
            val media = importer.import(listOf(uri)).imported.firstOrNull()
            if (media == null) {
                host.send(EditorEvent.NothingImported)
                return@launch
            }
            val overlay = overlayFromMedia(host.newId(), media.uri, media.info, host.positionMs)
            host.applyEdit(EditAction.AddOverlay(overlay))
            host.update { it.copy(selectedClipId = overlay.id, selectedTextId = null, selectedAudioId = null, selectedLayerId = null, activeTool = null) }
        }
    }

    fun onTiming(overlayId: String, timelineStartMs: Long, trimStartMs: Long, trimEndMs: Long) =
        host.applyEdit(EditAction.SetOverlayTiming(overlayId, timelineStartMs, trimStartMs, trimEndMs))

    fun onSplit() {
        val id = selected() ?: return
        host.applyEdit(EditAction.SplitOverlay(id, host.positionMs, host.newId()))
    }

    fun onDuplicate() {
        val id = selected() ?: return
        host.applyEdit(EditAction.DuplicateOverlay(id, host.newId()))
    }

    fun onDelete() {
        val id = selected() ?: return
        host.applyEdit(EditAction.DeleteOverlay(id))
        host.update { it.copy(selectedClipId = null, activeTool = null) }
    }

    /** "To main": into the main track at the clip boundary nearest the playhead. */
    fun onToMain() {
        val id = selected() ?: return
        val project = host.ready?.project ?: return
        host.applyEdit(EditAction.OverlayToMain(id, project.insertIndexAt(host.positionMs)))
    }

    /** "To overlay" for the selected (or playhead) main clip; the main track keeps at least one clip. */
    fun onToOverlay() {
        val project = host.ready?.project ?: return
        val clipId = host.targetClipId()?.takeIf { id -> project.clips.any { it.id == id } } ?: return
        if (project.clips.size < 2) {
            host.send(EditorEvent.KeepOneMainClip)
            return
        }
        host.applyEdit(EditAction.ClipToOverlay(clipId))
        host.update { it.copy(selectedClipId = clipId) }
    }

    private fun selected(): String? {
        val ready = host.ready ?: return null
        return ready.project.overlayById(ready.selectedClipId)?.id
    }
}
