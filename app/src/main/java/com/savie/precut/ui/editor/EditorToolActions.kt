package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.editor.tools.CoverActions
import com.savie.precut.ui.editor.tools.LayerToolbarActions
import com.savie.precut.ui.editor.tools.TextToolActions

/** Text panel callbacks routed to the ViewModel. */
@Composable
fun rememberTextToolActions(viewModel: EditorViewModel): TextToolActions = remember(viewModel) {
    object : TextToolActions {
        override fun onChange(overlay: TextOverlay) = viewModel.text.onChanged(overlay)
        override fun onSplit() = viewModel.text.onSplit()
        override fun onDuplicate() = viewModel.text.onDuplicate()
        override fun onDelete() = viewModel.text.onDelete()
        override fun onStyle() = viewModel.onToolSelected(EditorTool.TEXT_STYLE)
        override fun onRich() = viewModel.onToolSelected(EditorTool.TEXT_RICH)
        override fun onTts() = viewModel.onToolSelected(EditorTool.TEXT_TTS)
    }
}

/** Cover panel callbacks routed to the ViewModel's cover controller. */
@Composable
fun rememberCoverActions(viewModel: EditorViewModel): CoverActions = remember(viewModel) {
    object : CoverActions {
        override fun onUseFrame() = viewModel.cover.onUseFrame()
        override fun onPickImage() = viewModel.cover.onPickImage()
        override fun onReset() = viewModel.cover.onReset()
        override fun onInExportChanged(inExport: Boolean) = viewModel.cover.onInExportChanged(inExport)
    }
}

/** Look layer toolbar callbacks routed to the ViewModel's look controller. */
@Composable
fun rememberLayerToolbarActions(viewModel: EditorViewModel): LayerToolbarActions = remember(viewModel) {
    object : LayerToolbarActions {
        override fun onFilter() = viewModel.onToolSelected(EditorTool.FILTER)
        override fun onAdjust() = viewModel.onToolSelected(EditorTool.ADJUST)
        override fun onSplit() = viewModel.look.onSplitLayer()
        override fun onDuplicate() = viewModel.look.onDuplicateLayer()
        override fun onDelete() = viewModel.look.onDeleteLayer()
    }
}
