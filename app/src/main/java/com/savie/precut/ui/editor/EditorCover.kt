package com.savie.precut.ui.editor

import com.savie.precut.media.MediaImporter
import com.savie.precut.model.ClipType
import com.savie.precut.model.ProjectCover
import com.savie.precut.model.edit.EditAction
import kotlinx.coroutines.launch

/** The Cover tool: a frame under the playhead or a gallery photo, optionally the export's first frame. */
class EditorCover(private val host: EditorHost, private val importer: MediaImporter) {

    fun onOpen() {
        val ready = host.ready ?: return
        if (ready.project.clips.isEmpty()) return
        host.update { it.copy(activeTool = EditorTool.COVER, selectedClipId = null, selectedTextId = null, selectedAudioId = null, selectedLayerId = null) }
        (ready.project.cover as? ProjectCover.Frame)?.let { host.seekTo(it.timelineMs) }
    }

    /** Uses the frame under the playhead. */
    fun onUseFrame() = set(ProjectCover.Frame(host.positionMs))

    fun onPickImage() = host.send(EditorEvent.PickCoverImage)

    fun onImagePicked(uri: String?) {
        if (uri == null) return
        host.scope.launch {
            val photo = importer.import(listOf(uri)).imported.firstOrNull { it.info.type == ClipType.IMAGE }
            if (photo == null) {
                host.send(EditorEvent.NothingImported)
            } else {
                set(ProjectCover.Image(photo.uri, photo.info.rotationDegrees))
            }
        }
    }

    fun onReset() = host.applyEdit(EditAction.SetCover(cover = null, inExport = false))

    fun onInExportChanged(inExport: Boolean) {
        val cover = host.ready?.project?.cover ?: return
        host.applyEdit(EditAction.SetCover(cover, inExport))
    }

    private fun set(cover: ProjectCover) =
        host.applyEdit(EditAction.SetCover(cover, inExport = host.ready?.project?.coverInExport == true))
}
