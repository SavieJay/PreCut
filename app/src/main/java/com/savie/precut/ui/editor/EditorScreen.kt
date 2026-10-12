package com.savie.precut.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.ui.editor.preview.PlaybackControls
import com.savie.precut.ui.editor.preview.PreviewSurface
import com.savie.precut.ui.editor.tools.EditorToolbar
import com.savie.precut.ui.editor.tools.TrimTool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onExport: (projectId: String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (uiState is EditorUiState.Ready) {
                        TextButton(onClick = { onExport(viewModel.projectId) }) {
                            Text(stringResource(R.string.action_export))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (val state = uiState) {
            EditorUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            EditorUiState.NotFound -> Box(modifier, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.editor_not_found))
            }
            is EditorUiState.Ready -> Column(modifier) {
                PreviewSurface(
                    player = viewModel.player,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                PlaybackControls(
                    state = previewState,
                    onPlayPause = viewModel::onPlayPause,
                    onSeek = viewModel::onSeek,
                )
                // Phase 2 edits the first clip; Phase 3 adds clip selection on the timeline.
                val clip = state.project.clips.firstOrNull()
                if (state.activeTool == EditorTool.TRIM && clip != null) {
                    TrimTool(
                        clip = clip,
                        onTrim = { start, end -> viewModel.onTrim(clip.id, start, end) },
                        onDone = viewModel::onToolClosed,
                    )
                } else {
                    EditorToolbar(onToolSelected = viewModel::onToolSelected)
                }
            }
        }
    }
}
