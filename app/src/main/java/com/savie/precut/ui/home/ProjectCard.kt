package com.savie.precut.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.savie.precut.R
import com.savie.precut.data.CloudMark
import com.savie.precut.data.ProjectSummary
import com.savie.precut.ui.components.MediaBadge
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.components.projectDisplayName
import com.savie.precut.ui.theme.ClipBackground
import com.savie.precut.ui.theme.OnVideoChip
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glass
import java.io.File

private val CoverShape = RoundedCornerShape(18.dp)

/**
 * RD-H02: a glass draft card (24 dp corners, 8 dp inset) with the cover at its own ratio (18 dp
 * corners, duration badge), a round ⋮ on the cover, then name and "Edited …". The ⋮ and a
 * long-press open the card menu (RD-H03).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRemix: () -> Unit = onOpen,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .glass(Radius.card)
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().clip(CoverShape).background(ClipBackground)) {
            val path = project.coverPath
            if (path != null) {
                // FillWidth keeps the cover's own 9:16 or 1:1 shape, so the grid is masonry.
                AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f))
            }
            MediaBadge(formatDurationMs(project.durationMs), Modifier.align(Alignment.BottomEnd).padding(8.dp))
            // Phase 30C: in the account (a cloud), or waiting to upload (a clock).
            project.cloud?.let { mark ->
                MediaBadge(
                    icon = if (mark == CloudMark.Synced) R.drawable.ic_cloud else R.drawable.ic_schedule,
                    contentDescription = stringResource(if (mark == CloudMark.Synced) R.string.sync_synced else R.string.sync_pending),
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                )
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                MoreButton(onClick = { menuOpen = true }, modifier = Modifier.padding(4.dp))
                ProjectMenu(menuOpen, onDismiss = { menuOpen = false }, onRename, onDuplicate, onDelete, onRemix)
            }
        }
        Column(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp)) {
            Text(
                displayName(project),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.home_edited, DateUtils.getRelativeTimeSpanString(project.updatedAt).toString()),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

/** A 36 dp solid chip over the cover (never glass on footage), inside a 48 dp touch target. */
@Composable
private fun MoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_project_options)
    Box(
        modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(OnVideoChip), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = description, tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

/** The project's name, or "Project <date>" for unnamed drafts. */
@Composable
fun displayName(project: ProjectSummary): String = projectDisplayName(project.name, project.createdAt)
