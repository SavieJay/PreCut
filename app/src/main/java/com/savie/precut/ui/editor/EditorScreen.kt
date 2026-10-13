package com.savie.precut.ui.editor

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import com.savie.precut.ui.editor.preview.TextPositionOverlay
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
    // OpenDocument (not GetContent) so the music Uri can be kept across restarts.
    val musicPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        viewModel.audio.onMusicPicked(uri?.toString())
    }
    val micPermission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) viewModel.audio.onRecordStart(hasPermission = true)
    }
    ObserveAsEvents(viewModel.events) { event ->
        val message = when (event) {
            is EditorEvent.SomeFilesSkipped ->
                resources.getQuantityString(R.plurals.home_import_skipped, event.count, event.count)
            EditorEvent.NothingImported -> resources.getString(R.string.home_import_nothing)
            EditorEvent.SpeedNotForPhotos -> resources.getString(R.string.speed_not_for_photos)
            EditorEvent.MusicImportFailed -> resources.getString(R.string.audio_import_failed)
            EditorEvent.RecordingFailed -> resources.getString(R.string.voiceover_failed)
            EditorEvent.NothingToExtract -> resources.getString(R.string.audio_nothing_to_extract)
            EditorEvent.RequestMicPermission -> {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                return@ObserveAsEvents
            }
            EditorEvent.PickMusic -> {
                musicPicker.launch(arrayOf("audio/*"))
                return@ObserveAsEvents
            }
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val timelineActions = rememberTimelineActions(viewModel)

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
            // imePadding: the text tool's keyboard pushes the panel up and shrinks the preview.
            is EditorUiState.Ready -> Column(modifier.imePadding()) {
                if (state.project.clips.isEmpty()) {
                    // The player keeps its last frame after the final clip is deleted; cover it.
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.editor_empty), color = TextSecondary)
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PreviewSurface(player = viewModel.player, modifier = Modifier.fillMaxSize())
                        state.project.textOverlays.firstOrNull { it.id == state.selectedTextId }?.let { text ->
                            TextPositionOverlay(text, state.project.aspectRatio, onMoved = { x, y -> viewModel.onTextMoved(text.id, x, y) })
                        }
                    }
                }
                PlaybackControls(state = previewState, onPlayPause = viewModel::onPlayPause)
                TimelineSection(
                    state = state,
                    positionMs = previewState.positionMs,
                    loader = viewModel.thumbnails,
                    waveforms = viewModel.waveforms,
                    actions = timelineActions,
                    onAddMedia = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)) },
                )
                EditorBottomPanel(state, canSplit, previewState.positionMs, viewModel)
            }
        }
    }
}
