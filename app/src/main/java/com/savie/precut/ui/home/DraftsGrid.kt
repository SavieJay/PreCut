package com.savie.precut.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.data.ProjectSummary
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextPrimary

/** RD-H02's drafts: "Drafts · n" and AutoCut, then a two-column masonry of glass draft cards. */
@Composable
internal fun DraftsGrid(
    projects: List<ProjectSummary>,
    state: LazyStaggeredGridState,
    onAutoCut: () -> Unit,
    onOpen: (ProjectSummary) -> Unit,
    onRename: (ProjectSummary) -> Unit,
    onDuplicate: (ProjectSummary) -> Unit,
    onDelete: (ProjectSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = state,
        // Room at the foot for the floating New project pill.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        modifier = modifier,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_drafts_count, projects.size), style = PreCutType.panelTitle, color = TextPrimary, modifier = Modifier.weight(1f))
                TonalButton(stringResource(R.string.autocut_title), onAutoCut, icon = R.drawable.ic_beats)
            }
        }
        items(projects, key = { it.id }) { project ->
            ProjectCard(
                project = project,
                onOpen = { onOpen(project) },
                onRename = { onRename(project) },
                onDuplicate = { onDuplicate(project) },
                onDelete = { onDelete(project) },
            )
        }
    }
}
