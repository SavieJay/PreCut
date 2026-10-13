package com.savie.precut.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.data.ProjectSummary
import com.savie.precut.ui.components.CaptureMenu

/** What the drafts grid can do with a project. */
interface ProjectActions {
    fun onOpen(projectId: String)
    fun onRename(projectId: String, name: String)
    fun onDuplicate(projectId: String)
    fun onDelete(projectId: String)
}

/** CapCut-style Projects screen: drafts grid (or empty state) and a "New project" button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    isImporting: Boolean,
    projects: List<ProjectSummary>?,
    snackbarHostState: SnackbarHostState,
    onNewProject: () -> Unit,
    onCamera: (video: Boolean) -> Unit,
    actions: ProjectActions,
) {
    var renaming by remember { mutableStateOf<ProjectSummary?>(null) }
    var cameraMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        IconButton(onClick = { cameraMenu = true }, enabled = !isImporting) {
                            Icon(painterResource(R.drawable.ic_camera), contentDescription = stringResource(R.string.home_camera))
                        }
                        CaptureMenu(expanded = cameraMenu, onDismiss = { cameraMenu = false }, onCamera = onCamera)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isImporting) {
                ExtendedFloatingActionButton(
                    onClick = onNewProject,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_new_project)) },
                )
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.fillMaxSize().padding(innerPadding)
        when {
            isImporting || projects == null -> Box(modifier, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    if (isImporting) Text(stringResource(R.string.home_importing))
                }
            }
            projects.isEmpty() -> Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.home_empty_state),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = modifier,
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.home_drafts_title), style = MaterialTheme.typography.titleMedium)
                }
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { actions.onOpen(project.id) },
                        onRename = { renaming = project },
                        onDuplicate = { actions.onDuplicate(project.id) },
                        onDelete = { deleting = project },
                    )
                }
            }
        }
    }

    renaming?.let { project ->
        RenameDialog(
            initialName = displayName(project),
            onConfirm = { name ->
                actions.onRename(project.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { project ->
        DeleteDialog(
            name = displayName(project),
            onConfirm = {
                actions.onDelete(project.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
