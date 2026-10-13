package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import com.savie.precut.model.ClipType
import com.savie.precut.ui.editor.tools.FilterTool
import com.savie.precut.ui.editor.tools.SelectionActions
import com.savie.precut.ui.editor.tools.SelectionToolbar
import com.savie.precut.ui.editor.tools.SpeedTool

/** Multi-select mode: the selection toolbar, or a Speed/Filter/Volume panel acting on every picked item. */
@Composable
fun SelectionPanels(state: EditorUiState.Ready, picked: Set<String>, viewModel: EditorViewModel) {
    val selection = viewModel.selection
    val close = viewModel::onToolClosed
    val clips = state.project.clips.filter { it.id in picked }
    val audio = state.project.audioClips.filter { it.id in picked }
    // Panels show the first picked item's value as the starting point.
    val firstVideo = clips.firstOrNull { it.type == ClipType.VIDEO }
    when {
        state.activeTool == EditorTool.SPEED && firstVideo != null ->
            SpeedTool(firstVideo.speed, onSpeedChanged = selection::onSpeed, onDone = close)
        state.activeTool == EditorTool.FILTER && clips.isNotEmpty() ->
            FilterTool(clips.first(), viewModel.thumbnails, onFilterSelected = selection::onFilter, onDone = close)
        state.activeTool == EditorTool.VOLUME && (clips.isNotEmpty() || audio.isNotEmpty()) ->
            AudioVolumeTool(clips.firstOrNull()?.volume ?: audio.first().volume, selection::onVolume, onDone = close)
        else -> SelectionToolbar(
            count = picked.size,
            hasClips = clips.isNotEmpty(),
            actions = object : SelectionActions {
                override fun onDone() = selection.onDone()
                override fun onDelete() = selection.onDelete()
                override fun onDuplicate() = selection.onDuplicate()
                override fun onSpeed() = viewModel.onToolSelected(EditorTool.SPEED)
                override fun onFilter() = viewModel.onToolSelected(EditorTool.FILTER)
                override fun onVolume() = viewModel.onToolSelected(EditorTool.VOLUME)
            },
        )
    }
}
