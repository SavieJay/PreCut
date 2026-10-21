package com.savie.precut.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.ui.components.ObserveAsEvents
import com.savie.precut.ui.components.rememberCameraLaunchers
import com.savie.precut.ui.templates.TemplatesTab
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenEditor: (projectId: String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenTemplate: (templateId: String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current

    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        viewModel.onMediaPicked(uris.map(Uri::toString))
    }

    var tab by rememberSaveable { mutableStateOf(HomeTab.DRAFTS) }

    // AutoCut: pace (dialog), then clips, then a song.
    var autoCutDialog by rememberSaveable { mutableStateOf(false) }
    var autoCutEvery by rememberSaveable { mutableIntStateOf(1) }
    var autoCutMedia by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val songPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) viewModel.onAutoCut(autoCutMedia, uri.toString(), autoCutEvery)
        autoCutMedia = emptyList()
    }
    val autoCutPicker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            autoCutMedia = uris.map(Uri::toString)
            songPicker.launch(arrayOf("audio/*"))
        }
    }

    val camera = rememberCameraLaunchers(viewModel::onCaptureResult)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HomeEvent.OpenEditor -> onOpenEditor(event.projectId)
            is HomeEvent.LaunchCamera -> camera.launch(event.cameraUri, event.video)
            // A toast survives the navigation to the editor that follows this event.
            is HomeEvent.SomeFilesSkipped -> Toast.makeText(
                context,
                resources.getQuantityString(R.plurals.home_import_skipped, event.count, event.count),
                Toast.LENGTH_LONG,
            ).show()
            HomeEvent.NothingImported -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.home_import_nothing))
            }
            HomeEvent.AutoCutMusicUnreadable -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.autocut_music_unreadable))
            }
            HomeEvent.AutoCutNoBeats -> Toast.makeText(context, resources.getString(R.string.autocut_no_beats), Toast.LENGTH_LONG).show()
        }
    }

    HomeContent(
        isImporting = uiState.isImporting,
        projects = uiState.projects,
        snackbarHostState = snackbarHostState,
        onNewProject = {
            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
        },
        onCamera = viewModel::onCameraRequested,
        onAutoCut = { autoCutDialog = true },
        tab = tab,
        onTab = { tab = it },
        templates = { modifier -> TemplatesTab(onOpen = onOpenTemplate, modifier = modifier) },
        onSettings = onOpenSettings,
        actions = object : ProjectActions {
            override fun onOpen(projectId: String) = onOpenEditor(projectId)
            override fun onRename(projectId: String, name: String) = viewModel.onRename(projectId, name)
            override fun onDuplicate(projectId: String) = viewModel.onDuplicate(projectId)
            override fun onDelete(projectId: String) = viewModel.onDelete(projectId)
        },
    )
    if (autoCutDialog) {
        AutoCutDialog(
            every = autoCutEvery,
            onEvery = { autoCutEvery = it },
            onChooseClips = {
                autoCutDialog = false
                autoCutPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
            },
            onDismiss = { autoCutDialog = false },
        )
    }
}
