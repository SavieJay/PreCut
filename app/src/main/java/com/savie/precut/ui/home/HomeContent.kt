package com.savie.precut.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.data.ProjectSummary
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.theme.AccentFill
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextSecondary

/** What the drafts grid can do with a project. */
interface ProjectActions {
    fun onOpen(projectId: String)
    fun onRename(projectId: String, name: String)
    fun onDuplicate(projectId: String)
    fun onDelete(projectId: String)
}

/** Projects screen: drafts grid (H-02), empty state (H-01) or importing (H-07). */
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(tab == HomeTab.DRAFTS && !isImporting && !projects.isNullOrEmpty() && fabVisible, enter = scaleIn(), exit = scaleOut()) {
                FloatingActionButton(
                    onClick = onNewProject,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = AccentFill,
                    contentColor = OnAccent,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.home_new_project))
                }
            }
        },
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
                projects == null -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                projects.isEmpty() -> EmptyProjects(onNewProject, onAutoCut, modifier)
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 16.dp,
                    modifier = modifier,
                ) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.home_drafts_count, projects.size), style = PreCutType.section, color = TextSecondary, modifier = Modifier.weight(1f))
                            TonalButton(stringResource(R.string.autocut_title), onAutoCut, icon = R.drawable.ic_beats)
                        }
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
