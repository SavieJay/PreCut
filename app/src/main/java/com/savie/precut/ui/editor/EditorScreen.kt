package com.savie.precut.ui.editor

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.components.projectDisplayName
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.ui.components.ObserveAsEvents
import com.savie.precut.ui.editor.crop.CropTopBar
import com.savie.precut.ui.editor.preview.FullScreenPreview
import com.savie.precut.ui.components.rememberCameraLaunchers
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onExport: (projectId: String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Kept as a State and read only where needed: the playhead changes every frame while
    // playing or scrubbing, and the whole editor must not recompose with it.
    val previewState = viewModel.previewState.collectAsStateWithLifecycle()
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
    val replacementPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        viewModel.onReplacementPicked(uri?.toString())
    }
    val coverPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        viewModel.cover.onImagePicked(uri?.toString())
    }
    val canvasPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        viewModel.frame.onCanvasImagePicked(uri?.toString())
    }
    val overlayPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        viewModel.overlay.onPicked(uri?.toString())
    }
    val camera = rememberCameraLaunchers(viewModel::onCaptureResult)
    // `.cube` files have no standard MIME type, so any file can be picked and is validated.
    val lutPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        viewModel.look.onImportLut(uri?.toString())
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
            EditorEvent.FreezeNotForPhotos -> resources.getString(R.string.freeze_not_for_photos)
            EditorEvent.FreezeFailed -> resources.getString(R.string.freeze_failed)
            EditorEvent.ReverseNotForPhotos -> resources.getString(R.string.reverse_not_for_photos)
            EditorEvent.ReverseFailed -> resources.getString(R.string.reverse_failed)
            EditorEvent.ReverseUnsupported -> resources.getString(R.string.reverse_unsupported)
            EditorEvent.FrozeFrame, EditorEvent.ClipReversed, EditorEvent.ClipUnreversed -> {
                val text = when (event) {
                    EditorEvent.FrozeFrame -> R.string.freeze_added
                    EditorEvent.ClipReversed -> R.string.reverse_done
                    else -> R.string.reverse_removed
                }
                scope.launch {
                    val result = snackbarHostState.showSnackbar(resources.getString(text), actionLabel = resources.getString(R.string.action_undo))
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo()
                }
                return@ObserveAsEvents
            }
            EditorEvent.MusicImportFailed -> resources.getString(R.string.audio_import_failed)
            EditorEvent.RecordingFailed -> resources.getString(R.string.voiceover_failed)
            EditorEvent.NothingToExtract -> resources.getString(R.string.audio_nothing_to_extract)
            EditorEvent.PickReplacement -> {
                replacementPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                return@ObserveAsEvents
            }
            EditorEvent.LutImportFailed -> resources.getString(R.string.filter_lut_import_failed)
            EditorEvent.PickLut -> {
                lutPicker.launch(arrayOf("*/*"))
                return@ObserveAsEvents
            }
            EditorEvent.PickCoverImage -> {
                coverPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                return@ObserveAsEvents
            }
            EditorEvent.KeepOneMainClip -> resources.getString(R.string.overlay_keep_main)
            EditorEvent.PickOverlay -> {
                overlayPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                return@ObserveAsEvents
            }
            EditorEvent.PickCanvasImage -> {
                canvasPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                return@ObserveAsEvents
            }
            is EditorEvent.LaunchCamera -> {
                camera.launch(event.cameraUri, event.video)
                return@ObserveAsEvents
            }
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
    var renaming by rememberSaveable { mutableStateOf(false) }
    val topBarActions = remember(viewModel) {
        object : EditorTopBarActions {
            override fun onBack() = onBack()
            override fun onStartRename() {
                renaming = true
            }
            override fun onRename(name: String) {
                viewModel.onRename(name)
                renaming = false
            }
            override fun onCancelRename() {
                renaming = false
            }
            override fun onUndo() {
                viewModel.onUndo()
            }
            override fun onRedo() {
                viewModel.onRedo()
            }
            override fun onExport() = onExport(viewModel.projectId)
        }
    }
    BackHandler(enabled = renaming) { renaming = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                val ready = uiState as? EditorUiState.Ready
                if (ready?.cropDraft != null) {
                    CropTopBar(onCancel = viewModel.frame::onCropCancel, onApply = viewModel.frame::onCropApply)
                } else {
                    EditorTopBar(
                        title = ready?.project?.let { projectDisplayName(it.name, it.createdAt) }.orEmpty(),
                        canExport = ready != null && ready.project.clips.isNotEmpty() && ready.missingIds.isEmpty(),
                        canUndo = ready?.canUndo == true,
                        canRedo = ready?.canRedo == true,
                        renaming = renaming && ready != null,
                        actions = topBarActions,
                    )
                }
            },
            snackbarHost = {
                // Above the toolbar, which reaches down behind the navigation bar.
                SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding().padding(bottom = 72.dp))
            },
            // The top bar pads for the status bar and the bottom panel for the navigation bar.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                is EditorUiState.Ready -> EditorContent(
                    state = state,
                    dimmed = renaming,
                    previewState = previewState,
                    canSplit = canSplit,
                    viewModel = viewModel,
                    timelineActions = timelineActions,
                    onAddMedia = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)) },
                    modifier = modifier,
                )
            }
        }
        // Drawn over the whole screen, top bar included.
        (uiState as? EditorUiState.Ready)?.takeIf { it.fullScreen && it.project.clips.isNotEmpty() }?.let {
            FullScreenPreview(
                player = viewModel.player,
                previewState = previewState,
                onPlayPause = viewModel::onPlayPause,
                onScrubStart = viewModel::onScrubStart,
                onScrubEnd = viewModel::onScrubEnd,
                onSeek = viewModel::onSeek,
                onClose = { viewModel.onFullScreen(false) },
            )
        }
    }
}
