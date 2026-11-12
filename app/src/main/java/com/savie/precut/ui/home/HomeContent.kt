package com.savie.precut.ui.home

import com.savie.precut.ui.components.PreCutSnackbar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.data.ProjectSummary
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutSpinner
import com.savie.precut.ui.theme.afterglowBackground

/** What the drafts grid can do with a project. */
interface ProjectActions {
    fun onOpen(projectId: String)
    fun onRename(projectId: String, name: String)
    fun onDuplicate(projectId: String)
    fun onDelete(projectId: String)
}

/** Projects screen on the pink-glow background: drafts grid (RD-H02), empty state (RD-H01) or importing (RD-H07/H08). */
@Composable
fun HomeContent(
    isImporting: Boolean,
    projects: List<ProjectSummary>?,
    snackbarHostState: SnackbarHostState,
    onNewProject: () -> Unit,
    onCamera: (video: Boolean) -> Unit,
    onAutoCut: () -> Unit,
    actions: ProjectActions,
    /** AI Edit (Phase 27): pick media, then describe the video. */
    onAiEdit: () -> Unit = {},
    onSettings: () -> Unit = {},
    tab: HomeTab = HomeTab.DRAFTS,
    onTab: (HomeTab) -> Unit = {},
    templates: @Composable (Modifier) -> Unit = {},
    /** Above the tabs: the update banner when a newer version is out. */
    banner: @Composable () -> Unit = {},
) {
    var renaming by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }
    val gridState = rememberLazyStaggeredGridState()
    // Hidden while scrolling down the drafts, back as soon as the user scrolls up.
    val fabVisible by remember { derivedStateOf { !gridState.canScrollBackward || !gridState.lastScrolledForward } }

    Scaffold(
        topBar = { HomeTopBar(cameraEnabled = !isImporting, onCamera = onCamera, onSettings = onSettings, onAiEdit = onAiEdit) },
        snackbarHost = { SnackbarHost(snackbarHostState) { PreCutSnackbar(it) } },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            AnimatedVisibility(tab == HomeTab.DRAFTS && !isImporting && !projects.isNullOrEmpty() && fabVisible, enter = scaleIn(), exit = scaleOut()) {
                // RD-H02: the floating pink New project pill, centred over the drafts.
                FilledButton(
                    text = stringResource(R.string.home_new_project),
                    onClick = onNewProject,
                    icon = R.drawable.ic_add,
                    large = true,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        },
        containerColor = Color.Transparent,
        modifier = Modifier.afterglowBackground(),
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            banner()
            HomeTabs(tab, onTab)
            val modifier = Modifier.weight(1f).fillMaxWidth()
            if (tab == HomeTab.TEMPLATES) {
                templates(modifier)
                return@Column
            }
            when {
                isImporting -> ImportingMedia(modifier)
                projects == null -> Box(modifier, contentAlignment = Alignment.Center) { PreCutSpinner() }
                projects.isEmpty() -> EmptyProjects(onNewProject, onAutoCut, modifier)
                else -> DraftsGrid(
                    projects = projects,
                    state = gridState,
                    onAutoCut = onAutoCut,
                    onOpen = { actions.onOpen(it.id) },
                    onRename = { renaming = it },
                    onDuplicate = { actions.onDuplicate(it.id) },
                    onDelete = { deleting = it },
                    modifier = modifier,
                )
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
            coverPath = project.coverPath,
            onConfirm = {
                actions.onDelete(project.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
