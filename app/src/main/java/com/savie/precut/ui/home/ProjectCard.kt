package com.savie.precut.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.savie.precut.R
import com.savie.precut.data.ProjectSummary
import com.savie.precut.ui.components.MediaBadge
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.components.projectDisplayName
import com.savie.precut.ui.theme.ClipBackground
import com.savie.precut.ui.theme.ErrorRed
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import java.io.File

/**
 * H-02: a draft's cover at its own ratio (radius 12, duration badge), name, "Edited …" and a ⋮
 * menu (H-03). Long-press opens the same menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ClipBackground)) {
            val path = project.coverPath
            if (path != null) {
                // FillWidth keeps the cover's own 9:16 or 1:1 shape, so the grid is masonry.
                AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f))
            }
            MediaBadge(formatDurationMs(project.durationMs), Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    displayName(project),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.home_edited, DateUtils.getRelativeTimeSpanString(project.updatedAt).toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp).offset(x = 12.dp, y = (-8).dp)) {
                    Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.home_project_options), modifier = Modifier.size(20.dp))
                }
                ProjectMenu(menuOpen, onDismiss = { menuOpen = false }, onRename, onDuplicate, onDelete)
            }
        }
    }
}

/** H-03: Rename, Duplicate, Delete (in ErrorRed), on an Ink2 menu with 12 dp corners. */
@Composable
private fun ProjectMenu(expanded: Boolean, onDismiss: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(12.dp), containerColor = Ink2) {
        MenuRow(R.drawable.ic_edit, stringResource(R.string.action_rename), TextPrimary) { onDismiss(); onRename() }
        MenuRow(R.drawable.ic_duplicate, stringResource(R.string.action_duplicate), TextPrimary) { onDismiss(); onDuplicate() }
        MenuRow(R.drawable.ic_delete, stringResource(R.string.action_delete), ErrorRed) { onDismiss(); onDelete() }
    }
}

@Composable
private fun MenuRow(icon: Int, label: String, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = color) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = if (color == TextPrimary) TextSecondary else color) },
        onClick = onClick,
    )
}

/** The project's name, or "Project <date>" for unnamed drafts. */
@Composable
fun displayName(project: ProjectSummary): String = projectDisplayName(project.name, project.createdAt)
