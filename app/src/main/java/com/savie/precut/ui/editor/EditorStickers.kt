package com.savie.precut.ui.editor

import android.net.Uri
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.sticker.DrawingRasterizer
import com.savie.precut.media.sticker.LibraryImage
import com.savie.precut.model.outputSize
import com.savie.precut.model.sticker.Drawing
import kotlinx.coroutines.flow.update
import com.savie.precut.media.sticker.StickerLibrary
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.frameRatio
import com.savie.precut.model.sticker.Sticker
import com.savie.precut.model.sticker.StickerKind
import com.savie.precut.model.sticker.stickerOverlay
import com.savie.precut.media.TEXT_REFERENCE_WIDTH_DP
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The sticker sheet (S-01), the selected sticker's tools (S-01b) and Draw (S-02). */
class EditorStickers(
    private val host: EditorHost,
    private val library: StickerLibrary,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _images = MutableStateFlow<List<LibraryImage>>(emptyList())
    val images: StateFlow<List<LibraryImage>> = _images.asStateFlow()

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private val _drawing = MutableStateFlow(Drawing())
    /** The drawing in progress while Draw is open. */
    val drawing: StateFlow<Drawing> = _drawing.asStateFlow()

    /** Opens the sheet (or Draw), nothing selected. */
    fun open(tool: EditorTool) {
        val ready = host.ready ?: return
        if (ready.project.clips.isEmpty()) return
        host.update { it.copy(activeTool = tool, selectedTextId = null, selectedClipId = null, selectedAudioId = null, selectedLayerId = null) }
        if (tool == EditorTool.STICKERS) refresh()
        // A new drawing keeps the last brush.
        if (tool == EditorTool.DRAW) _drawing.update { Drawing(colorArgb = it.colorArgb, size = it.size) }
    }

    fun onEmoji(emoji: String) {
        host.scope.launch { withContext(io) { library.useEmoji(emoji) } }
        add(Sticker(StickerKind.EMOJI, emoji))
    }

    fun onImage(image: LibraryImage) {
        host.scope.launch {
            val sticker = withContext(io) { library.place(image) }
            if (sticker == null) host.send(EditorEvent.StickerNotUsable) else add(sticker)
        }
    }

    fun onImport() = host.send(EditorEvent.PickStickerImage)

    /** A picture picked for "My images": kept there and placed right away. */
    fun onImagePicked(uri: String?) {
        if (uri == null) return
        host.scope.launch {
            val image = withContext(io) { library.import(Uri.parse(uri)) }
            if (image == null) {
                host.send(EditorEvent.StickerNotUsable)
                return@launch
            }
            refresh()
            onImage(image)
        }
    }

    fun onRemoveImage(image: LibraryImage) {
        host.scope.launch {
            withContext(io) { library.remove(image) }
            refresh()
            host.send(EditorEvent.StickerImageRemoved)
        }
    }

    fun onStroke(points: List<Pair<Float, Float>>) = _drawing.update { it.withStroke(points) }
    fun onUndoStroke() = _drawing.update { it.undo() }
    fun onRedoStroke() = _drawing.update { it.redo() }
    fun onEraser(eraser: Boolean) = _drawing.update { it.copy(eraser = eraser) }
    fun onDrawSize(size: Float) = _drawing.update { it.copy(size = size) }
    fun onDrawColor(argb: Int) = _drawing.update { it.copy(colorArgb = argb, eraser = false) }

    /** The finished drawing becomes a full-frame sticker at the playhead. */
    fun onDrawingDone() {
        val ready = host.ready ?: return
        val drawing = _drawing.value
        if (drawing.isBlank) return onDrawingDiscarded()
        val frame = ready.project.outputSize()
        host.scope.launch {
            val sticker = withContext(io) { library.saveDrawing(DrawingRasterizer.rasterize(drawing, frame)) }
            if (sticker == null) {
                host.send(EditorEvent.DrawingNotSaved)
                return@launch
            }
            val ready = host.ready ?: return@launch
            // Sizes are sp on the frame's short side; the drawing fills the frame's height.
            val (w, h) = ready.project.frameRatio()
            val heightSp = TEXT_REFERENCE_WIDTH_DP * h / minOf(w, h)
            add(sticker, heightSp)
        }
    }

    fun onDrawingDiscarded() = host.update { it.copy(activeTool = null) }

    /** Opacity (S-01b) for the selected sticker, as one edit on release. */
    fun onOpacity(opacity: Float) {
        val sticker = selected() ?: return
        host.applyEdit(EditAction.UpdateText(sticker.copy(opacity = opacity.coerceIn(0f, 1f))))
    }

    private fun add(sticker: Sticker, heightSp: Float? = null) {
        val overlay = stickerOverlay(host.newId(), sticker, host.positionMs, heightSp)
        host.applyEdit(EditAction.AddText(overlay))
        host.update { it.copy(selectedTextId = overlay.id, selectedClipId = null, selectedLayerId = null, activeTool = null) }
    }

    private fun refresh() {
        host.scope.launch {
            _images.value = withContext(io) { library.images() }
            _recent.value = withContext(io) { library.recentEmoji() }
        }
    }

    private fun selected() = host.ready?.let { ready -> ready.project.textOverlays.firstOrNull { it.id == ready.selectedTextId && it.sticker != null } }
}
