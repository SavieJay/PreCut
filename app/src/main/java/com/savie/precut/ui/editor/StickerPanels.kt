package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.media.sticker.LibraryImage
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.sticker.DEFAULT_STICKER_DURATION_MS
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.editor.tools.sticker.DrawActions
import com.savie.precut.ui.editor.tools.sticker.DrawTool
import com.savie.precut.ui.editor.tools.sticker.StickerAnimationTool
import com.savie.precut.ui.editor.tools.sticker.StickerOpacityTool
import com.savie.precut.ui.editor.tools.sticker.StickerSheet
import com.savie.precut.ui.editor.tools.sticker.StickerSheetActions
import com.savie.precut.ui.editor.tools.sticker.StickerToolbar
import com.savie.precut.ui.editor.tools.sticker.StickerToolbarActions

/** Tools that open with nothing selected: the sticker sheet and Draw. */
val STICKER_SHEET_TOOLS = setOf(EditorTool.STICKERS, EditorTool.DRAW)

/** The sticker sheet (S-01) or Draw (S-02). */
@Composable
fun StickerSheetPanels(tool: EditorTool, positionMs: () -> Long, viewModel: EditorViewModel) {
    val stickers = viewModel.stickers
    val close = viewModel::onToolClosed
    if (tool == EditorTool.DRAW) {
        val drawing by stickers.drawing.collectAsStateWithLifecycle()
        val actions = remember(stickers) {
            object : DrawActions {
                override fun onUndo() = stickers.onUndoStroke()
                override fun onRedo() = stickers.onRedoStroke()
                override fun onEraser(eraser: Boolean) = stickers.onEraser(eraser)
                override fun onSize(size: Float) = stickers.onDrawSize(size)
                override fun onColor(argb: Int) = stickers.onDrawColor(argb)
                override fun onDiscard() = stickers.onDrawingDiscarded()
                override fun onDone() = stickers.onDrawingDone()
            }
        }
        val note = stringResource(R.string.draw_note, formatSeconds(DEFAULT_STICKER_DURATION_MS), formatDurationMs(positionMs()))
        DrawTool(drawing, note, actions)
        return
    }
    val recent by stickers.recent.collectAsStateWithLifecycle()
    val images by stickers.images.collectAsStateWithLifecycle()
    val actions = remember(stickers) {
        object : StickerSheetActions {
            override fun onEmoji(emoji: String) = stickers.onEmoji(emoji)
            override fun onImage(image: LibraryImage) = stickers.onImage(image)
            override fun onImport() = stickers.onImport()
            override fun onRemoveImage(image: LibraryImage) = stickers.onRemoveImage(image)
        }
    }
    StickerSheet(recent, images, actions, onClose = close)
}

/** The selected sticker's toolbar (S-01b), or its Animation or Opacity panel. */
@Composable
fun SelectedStickerPanels(state: EditorUiState.Ready, sticker: TextOverlay, positionMs: () -> Long, viewModel: EditorViewModel) {
    val close = viewModel::onToolClosed
    val haptics = LocalHapticFeedback.current
    when (state.activeTool) {
        EditorTool.STICKER_ANIMATION -> StickerAnimationTool(sticker, viewModel.text::onChanged, onDone = close)
        EditorTool.STICKER_OPACITY -> StickerOpacityTool(sticker.opacity, viewModel.stickers::onOpacity, onDone = close)
        else -> StickerToolbar(
            canSplit = rememberCanSplit(positionMs, sticker.startMs, sticker.endMs),
            actions = remember(viewModel) {
                object : StickerToolbarActions {
                    override fun onAnimation() = viewModel.onToolSelected(EditorTool.STICKER_ANIMATION)
                    override fun onOpacity() = viewModel.onToolSelected(EditorTool.STICKER_OPACITY)
                    override fun onKeyframe() = viewModel.keyframes.onToggle()
                    override fun onSplit() {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.text.onSplit()
                    }
                    override fun onDuplicate() = viewModel.text.onDuplicate()
                    override fun onDelete() {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.text.onDelete()
                    }
                }
            },
        )
    }
}
