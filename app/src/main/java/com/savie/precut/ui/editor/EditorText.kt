package com.savie.precut.ui.editor

import com.savie.precut.model.TextOverlay
import com.savie.precut.model.edit.EditAction
import com.savie.precut.ui.editor.preview.TextTransform

/** Text overlay editing for the editor; all edits go through [EditorHost.applyEdit] (the reducer). */
class EditorText(private val host: EditorHost) {

    /** New text at the playhead, lasting [DEFAULT_TEXT_DURATION_MS], selected for editing. */
    fun onAdd() {
        val ready = host.ready ?: return
        if (ready.project.clips.isEmpty()) return
        val start = host.positionMs
        val overlay = TextOverlay(id = host.newId(), text = "", startMs = start, endMs = start + DEFAULT_TEXT_DURATION_MS)
        host.applyEdit(EditAction.AddText(overlay))
        host.update { it.copy(selectedTextId = overlay.id, selectedClipId = null, selectedLayerId = null, activeTool = EditorTool.TEXT) }
    }

    fun onChanged(overlay: TextOverlay) = host.applyEdit(EditAction.UpdateText(overlay))

    /** Move, pinch-scale and rotate on the preview, as one edit. */
    fun onTransformed(textId: String, transform: TextTransform) {
        val overlay = find(textId) ?: return
        host.applyEdit(
            EditAction.UpdateText(
                overlay.copy(
                    xNorm = transform.xNorm,
                    yNorm = transform.yNorm,
                    textSizeSp = transform.sizeSp,
                    rotationDegrees = transform.rotationDegrees,
                ),
            ),
        )
    }

    fun onTiming(textId: String, startMs: Long, endMs: Long) {
        val overlay = find(textId) ?: return
        host.applyEdit(EditAction.UpdateText(overlay.copy(startMs = startMs, endMs = endMs)))
    }

    /** Splits at the playhead and keeps editing the part after it (CapCut). */
    fun onSplit() {
        val textId = host.ready?.selectedTextId ?: return
        val newId = host.newId()
        host.applyEdit(EditAction.SplitText(textId, host.positionMs, newId))
        if (find(newId) != null) host.update { it.copy(selectedTextId = newId) }
    }

    fun onDuplicate() {
        val textId = host.ready?.selectedTextId ?: return
        val newId = host.newId()
        host.applyEdit(EditAction.DuplicateText(textId, newId))
        if (find(newId) != null) host.update { it.copy(selectedTextId = newId) }
    }

    fun onDelete() {
        val textId = host.ready?.selectedTextId ?: return
        host.applyEdit(EditAction.DeleteText(textId))
        host.update { it.copy(selectedTextId = null, activeTool = null) }
    }

    /** A text left empty when its panel closes is removed rather than kept invisible. */
    fun dropBlankSelected() {
        val text = host.ready?.selectedTextId?.let(::find) ?: return
        if (text.text.isBlank()) host.applyEdit(EditAction.DeleteText(text.id))
    }

    private fun find(textId: String): TextOverlay? = host.ready?.project?.textOverlays?.firstOrNull { it.id == textId }

    private companion object {
        const val DEFAULT_TEXT_DURATION_MS = 3_000L
    }
}
