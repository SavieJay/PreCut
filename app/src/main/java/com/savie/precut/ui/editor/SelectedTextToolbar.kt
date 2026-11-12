package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.editor.tools.text.TextToolbar
import com.savie.precut.ui.editor.tools.text.TextToolbarActions

/** The selected text's dock (RD-X02, Phase 29E), wired to the actions the text panels already use. */
@Composable
fun SelectedTextToolbar(text: TextOverlay, positionMs: () -> Long, viewModel: EditorViewModel) {
    val haptics = LocalHapticFeedback.current
    TextToolbar(
        canSplit = rememberCanSplit(positionMs, text.startMs, text.endMs),
        actions = remember(viewModel) {
            object : TextToolbarActions {
                override fun onEdit() = viewModel.onTextEdit()
                override fun onStyle(tab: Int) = viewModel.onTextStyleTab(tab)
                override fun onRich() = viewModel.onToolSelected(EditorTool.TEXT_RICH)
                override fun onTts() = viewModel.onToolSelected(EditorTool.TEXT_TTS)
                override fun onCaptions() = viewModel.onCaptions()
                override fun onFollow() = viewModel.reframe.onFollow()
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
