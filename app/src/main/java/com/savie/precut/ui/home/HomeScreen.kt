package com.savie.precut.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.ui.components.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenEditor: (projectId: String) -> Unit,
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

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HomeEvent.OpenEditor -> onOpenEditor(event.projectId)
            // A toast survives the navigation to the editor that follows this event.
            is HomeEvent.SomeFilesSkipped -> Toast.makeText(
                context,
                resources.getQuantityString(R.plurals.home_import_skipped, event.count, event.count),
                Toast.LENGTH_LONG,
            ).show()
            HomeEvent.NothingImported -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.home_import_nothing))
            }
        }
    }

    HomeContent(
        isImporting = uiState.isImporting,
        snackbarHostState = snackbarHostState,
        onNewProject = {
            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
        },
    )
}
