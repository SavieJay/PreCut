package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import com.savie.precut.ui.editor.tools.EditorToolbar
import com.savie.precut.ui.editor.tools.ToolbarActions

/** The main tool row, wired to the ViewModel. */
@Composable
fun MainToolbar(state: EditorUiState.Ready, canSplit: Boolean, viewModel: EditorViewModel) {
    EditorToolbar(
        canSplit = canSplit,
        hasClips = state.project.clips.isNotEmpty(),
        actions = object : ToolbarActions {
            override fun onSplit() = viewModel.onSplit()
            override fun onTrim() = viewModel.onToolSelected(EditorTool.TRIM)
            override fun onSpeed() = viewModel.onToolSelected(EditorTool.SPEED)
            override fun onFilters() = viewModel.onToolSelected(EditorTool.FILTER)
            override fun onVolume() = viewModel.onToolSelected(EditorTool.VOLUME)
            override fun onFade() = viewModel.onToolSelected(EditorTool.FADE)
            override fun onAudio() = viewModel.onAudioMenu(true)
            override fun onExtractAudio() = viewModel.audio.onExtract()
            override fun onRotate() = viewModel.onRotate()
            override fun onReplace() = viewModel.onReplaceClip()
            override fun onText() = viewModel.onToolSelected(EditorTool.TEXT)
            override fun onRatio() = viewModel.onToolSelected(EditorTool.RATIO)
            override fun onDuplicate() = viewModel.onDuplicate()
            override fun onDelete() = viewModel.onDelete()
            override fun onSelect() = viewModel.selection.onStart()
            override fun onCover() = viewModel.onToolSelected(EditorTool.COVER)
        },
    )
}
