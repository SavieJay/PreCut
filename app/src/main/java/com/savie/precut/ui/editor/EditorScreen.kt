package com.savie.precut.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.ui.components.ObserveAsEvents
import com.savie.precut.ui.editor.preview.PlaybackControls
import com.savie.precut.ui.editor.preview.PreviewSurface
import com.savie.precut.ui.editor.timeline.TimelineActions
import com.savie.precut.ui.editor.tools.EditorToolbar
import com.savie.precut.ui.editor.tools.TrimTool
import com.savie.precut.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onExport: (projectId: String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val canSplit by viewModel.canSplit.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }

    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        viewModel.onMediaPicked(uris.map(Uri::toString))
    }
    ObserveAsEvents(viewModel.events) { event ->
        val message = when (event) {
            is EditorEvent.SomeFilesSkipped ->
                resources.getQuantityString(R.plurals.home_import_skipped, event.count, event.count)
            EditorEvent.NothingImported -> resources.getString(R.string.home_import_nothing)
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val timelineActions = remember(viewModel) {
        object : TimelineActions {
            override fun onScrubStart() = viewModel.onScrubStart()
            override fun onSeek(positionMs: Long) = viewModel.onSeek(positionMs)
            override fun onSelectClip(clipId: String?) = viewModel.onClipSelected(clipId)
            override fun onTrim(clipId: String, trimStartMs: Long, trimEndMs: Long) =
                viewModel.onTrim(clipId, trimStartMs, trimEndMs)
            override fun onMove(from: Int, to: Int) = viewModel.onMove(from, to)
        }
    }

    Scaffold(
        topBar = {
            val ready = uiState as? EditorUiState.Ready
            EditorTopBar(
                canExport = ready != null && ready.project.clips.isNotEmpty(),
                onBack = onBack,
                onExport = { onExport(viewModel.projectId) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                if (state.project.clips.isEmpty()) {
                    // The player keeps its last frame after the final clip is deleted; cover it.
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.editor_empty), color = TextSecondary)
                    }
                } else {
                    PreviewSurface(player = viewModel.player, modifier = Modifier.weight(1f).fillMaxWidth())
                }
                PlaybackControls(state = previewState, onPlayPause = viewModel::onPlayPause)
                TimelineSection(
                    state = state,
                    positionMs = previewState.positionMs,
                    loader = viewModel.thumbnails,
                    actions = timelineActions,
                    onAddMedia = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)) },
                )
                val toolClip = state.project.clips.firstOrNull { it.id == state.selectedClipId }
                if (state.activeTool == EditorTool.TRIM && toolClip != null) {
                    TrimTool(
                        clip = toolClip,
                        onTrim = { start, end -> viewModel.onTrim(toolClip.id, start, end) },
                        onDone = viewModel::onToolClosed,
                    )
                } else {
                    EditorToolbar(
                        canSplit = canSplit,
                        hasClips = state.project.clips.isNotEmpty(),
                        onSplit = viewModel::onSplit,
                        onTrim = { viewModel.onToolSelected(EditorTool.TRIM) },
                        onDuplicate = viewModel::onDuplicate,
                        onDelete = viewModel::onDelete,
                    )
                }
            }
        }
    }
}
