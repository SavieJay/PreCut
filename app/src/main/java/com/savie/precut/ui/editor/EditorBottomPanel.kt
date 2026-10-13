package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import com.savie.precut.ui.editor.tools.EditorToolbar
import com.savie.precut.ui.editor.tools.FilterTool
import com.savie.precut.ui.editor.tools.RatioTool
import com.savie.precut.ui.editor.tools.SpeedTool
import com.savie.precut.ui.editor.tools.TextTool
import com.savie.precut.ui.editor.tools.ToolbarActions
import com.savie.precut.ui.editor.tools.TrimTool

/** The open tool's panel, or the toolbar when no tool is open (or its target disappeared). */
@Composable
fun EditorBottomPanel(state: EditorUiState.Ready, canSplit: Boolean, viewModel: EditorViewModel) {
    val clip = state.project.clips.firstOrNull { it.id == state.selectedClipId }
    val text = state.project.textOverlays.firstOrNull { it.id == state.selectedTextId }
    when {
        state.activeTool == EditorTool.TRIM && clip != null ->
            TrimTool(clip, onTrim = { start, end -> viewModel.onTrim(clip.id, start, end) }, onDone = viewModel::onToolClosed)
        state.activeTool == EditorTool.SPEED && clip != null ->
            SpeedTool(clip.speed, onSpeedChanged = viewModel::onSpeedChanged, onDone = viewModel::onToolClosed)
        state.activeTool == EditorTool.FILTER && clip != null ->
            FilterTool(clip, viewModel.thumbnails, onFilterSelected = viewModel::onFilterSelected, onDone = viewModel::onToolClosed)
        state.activeTool == EditorTool.TEXT && text != null ->
            TextTool(text, onChange = viewModel::onTextChanged, onDelete = viewModel::onDeleteText, onDone = viewModel::onToolClosed)
        state.activeTool == EditorTool.RATIO ->
            RatioTool(state.project.aspectRatio, onSelected = viewModel::onAspectRatioSelected, onDone = viewModel::onToolClosed)
        else -> EditorToolbar(
            canSplit = canSplit,
            hasClips = state.project.clips.isNotEmpty(),
            actions = object : ToolbarActions {
                override fun onSplit() = viewModel.onSplit()
                override fun onTrim() = viewModel.onToolSelected(EditorTool.TRIM)
                override fun onSpeed() = viewModel.onToolSelected(EditorTool.SPEED)
                override fun onFilters() = viewModel.onToolSelected(EditorTool.FILTER)
                override fun onRotate() = viewModel.onRotate()
                override fun onText() = viewModel.onToolSelected(EditorTool.TEXT)
                override fun onRatio() = viewModel.onToolSelected(EditorTool.RATIO)
                override fun onDuplicate() = viewModel.onDuplicate()
                override fun onDelete() = viewModel.onDelete()
            },
        )
    }
}
