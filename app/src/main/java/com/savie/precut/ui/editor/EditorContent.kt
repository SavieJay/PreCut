package com.savie.precut.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.PreviewState
import com.savie.precut.model.outputSize
import com.savie.precut.ui.editor.preview.PlaybackControls
import com.savie.precut.ui.editor.preview.PreviewSurface
import com.savie.precut.ui.editor.preview.ProxyStatusChip
import com.savie.precut.ui.editor.preview.TextTransformOverlay
import com.savie.precut.ui.editor.timeline.TimelineActions
import com.savie.precut.ui.theme.TextSecondary

/** The loaded editor: missing-media banner, preview, playback bar, timeline and bottom panel. */
@Composable
fun EditorContent(
    state: EditorUiState.Ready,
    previewState: State<PreviewState>,
    canSplit: Boolean,
    viewModel: EditorViewModel,
    timelineActions: TimelineActions,
    onAddMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // imePadding: the text tool's keyboard pushes the panel up and shrinks the preview.
    Column(modifier.imePadding()) {
        if (state.missingIds.isNotEmpty()) {
            MissingMediaBanner(state.missingIds.size, onRemoveAll = viewModel::onRemoveMissing)
        }
        if (state.project.clips.isEmpty()) {
            // The player keeps its last frame after the final clip is deleted; cover it.
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.editor_empty), color = TextSecondary)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                ProxyStatusChip(viewModel.proxyProgress, Modifier.align(Alignment.TopStart).padding(8.dp))
                // While full screen shows the video, this surface steps aside: one player can
                // only render to one surface, and re-attaching on return is reliable.
                if (!state.fullScreen) PreviewSurface(player = viewModel.player, modifier = Modifier.fillMaxSize())
                state.project.textOverlays.firstOrNull { it.id == state.selectedTextId }?.let { text ->
                    TextTransformOverlay(text, state.project.outputSize(), onTransformed = { viewModel.text.onTransformed(text.id, it) })
                }
            }
        }
        LivePlaybackControls(previewState, onPlayPause = viewModel::onPlayPause, onFullScreen = { viewModel.onFullScreen(true) })
        TimelineSection(
            state = state,
            positionMs = { previewState.value.positionMs },
            loader = viewModel.thumbnails,
            waveforms = viewModel.waveforms,
            actions = timelineActions,
            onAddMedia = onAddMedia,
            onCamera = viewModel::onCameraRequested,
        )
        EditorBottomPanel(state, canSplit, { previewState.value.positionMs }, viewModel)
    }
}

/** Reads the preview state in its own scope, so only the controls recompose as time moves. */
@Composable
private fun LivePlaybackControls(previewState: State<PreviewState>, onPlayPause: () -> Unit, onFullScreen: () -> Unit) {
    PlaybackControls(state = previewState.value, onPlayPause = onPlayPause, onFullScreen = onFullScreen)
}
