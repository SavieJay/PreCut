package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.savie.precut.ui.editor.tools.EditorToolbar
import com.savie.precut.ui.editor.tools.OverlayToolbar
import com.savie.precut.ui.editor.tools.OverlayToolbarActions
import com.savie.precut.ui.editor.tools.ToolbarActions

/** The overlay tool row (P-02), wired to the ViewModel. */
@Composable
fun OverlayToolbarFor(viewModel: EditorViewModel) {
    OverlayToolbar(
        remember(viewModel) {
            object : OverlayToolbarActions {
                override fun onAnimation() = viewModel.onToolSelected(EditorTool.ANIMATION)
                override fun onOpacity() = viewModel.onToolSelected(EditorTool.OPACITY)
                override fun onFilters() = viewModel.onToolSelected(EditorTool.FILTER)
                override fun onAdjust() = viewModel.onToolSelected(EditorTool.ADJUST)
                override fun onToMain() = viewModel.overlay.onToMain()
                override fun onSplit() = viewModel.overlay.onSplit()
                override fun onDuplicate() = viewModel.overlay.onDuplicate()
                override fun onDelete() = viewModel.overlay.onDelete()
            }
        },
    )
}

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
            override fun onAdjust() = viewModel.onToolSelected(EditorTool.ADJUST)
            override fun onVolume() = viewModel.onToolSelected(EditorTool.VOLUME)
            override fun onFade() = viewModel.onToolSelected(EditorTool.FADE)
            override fun onAudio() = viewModel.onAudioMenu(true)
            override fun onExtractAudio() = viewModel.audio.onExtract()
            override fun onEdit() = viewModel.onToolSelected(EditorTool.EDIT)
            override fun onCanvas() = viewModel.onToolSelected(EditorTool.CANVAS)
            override fun onOpacity() = viewModel.onToolSelected(EditorTool.OPACITY)
            override fun onAnimation() = viewModel.onToolSelected(EditorTool.ANIMATION)
            override fun onFreeze() = viewModel.motion.onFreeze()
            override fun onReverse() = viewModel.reverse.onReverse()
            override fun onOverlay() = viewModel.overlay.onAdd()
            override fun onToOverlay() = viewModel.overlay.onToOverlay()
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
