package com.savie.precut.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.savie.precut.R
import com.savie.precut.export.ExportKind
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.MediaBadge
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.PreCutTopBar
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.ClipBackground
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Radius
import java.io.File

// EX-04 done (RD-EX04) and the cover the export screens show; failures are in ExportFailed.kt.

@Composable
internal fun ColumnScope.Done(state: ExportUiState.Done, info: ExportInfo?, actions: ExportActions) {
    val title = when (state.kind) {
        ExportKind.AUDIO -> R.string.export_done_audio
        ExportKind.GIF -> R.string.export_done_gif
        ExportKind.VIDEO -> R.string.export_done
    }
    PreCutTopBar(stringResource(title), subtitle = info?.let { qualityOf(it) }, onBack = actions::onBackToEdit) {
        state.uri?.let { uri -> PreCutIconButton(R.drawable.ic_open_in_new, stringResource(R.string.action_open), { actions.onOpen(uri) }) }
        PreCutIconButton(R.drawable.ic_home, stringResource(R.string.export_projects), actions::onProjects)
    }
    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .height(360.dp)
                .aspectRatio(9f / 16f)
                .shadow(32.dp, Radius.tile, ambientColor = Color.Transparent, spotColor = Accent),
        ) {
            Cover(info?.coverPath, Modifier.matchParentSize(), radius = 18)
            Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.1f), Radius.tile))
            state.uri?.let { uri ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .shadow(8.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.export_play)) { actions.onOpen(uri) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = stringResource(R.string.export_play), tint = Ink0, modifier = Modifier.size(30.dp))
                }
            }
            info?.let { MediaBadge(formatDurationMs(it.durationMs), Modifier.align(Alignment.BottomEnd).padding(8.dp)) }
        }
    }
    state.uri?.let { uri ->
        FilledButton(
            stringResource(R.string.action_share),
            { actions.onShare(uri) },
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            icon = R.drawable.ic_share,
            large = true,
        )
    }
}

/** The draft cover (first frame) or a placeholder. */
@Composable
internal fun Cover(path: String?, modifier: Modifier, radius: Int = 18) {
    Box(modifier.clip(RoundedCornerShape(radius.dp)).background(ClipBackground)) {
        path?.let { AsyncImage(model = File(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}
