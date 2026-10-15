package com.savie.precut.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.MediaBadge
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.ClipBackground
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.SnapGuide
import com.savie.precut.ui.theme.TextSecondary
import java.io.File

// EX-04 done and EX-05 failure screens, and the cover both export screens show.

@Composable
internal fun ColumnScope.Done(state: ExportUiState.Done, info: ExportInfo?, actions: ExportActions) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions::onBackToEdit) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.export_back_to_edit)) }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = actions::onProjects) { Icon(painterResource(R.drawable.ic_home), stringResource(R.string.export_projects)) }
    }
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.weight(1f, fill = false).size(240.dp, 426.dp)) {
            Cover(info?.coverPath, Modifier.matchParentSize(), radius = 16)
            state.uri?.let { uri ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Ink0.copy(alpha = 0.7f))
                        .clickable(onClickLabel = stringResource(R.string.export_play)) { actions.onOpen(uri) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = stringResource(R.string.export_play), tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            info?.let { MediaBadge(formatDurationMs(it.durationMs), Modifier.align(Alignment.BottomEnd).padding(10.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = SnapGuide)
            Text(stringResource(R.string.export_done), style = MaterialTheme.typography.titleMedium)
        }
        state.uri?.let { uri ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TonalButton(stringResource(R.string.action_open), { actions.onOpen(uri) }, Modifier.weight(1f).height(48.dp), icon = R.drawable.ic_open_in_new)
                FilledButton(stringResource(R.string.action_share), { actions.onShare(uri) }, Modifier.weight(1f).height(48.dp), icon = R.drawable.ic_share)
            }
        }
    }
}

@Composable
internal fun ColumnScope.Failed(state: ExportUiState.Failed, actions: ExportActions) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Text(stringResource(state.reason.messageRes()), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        state.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center) }
        if (state.reason != ExportFailure.PROJECT_NOT_FOUND) FilledButton(stringResource(R.string.action_retry), actions::onRetry)
        AccentTextButton(stringResource(R.string.export_back_to_edit), actions::onBackToEdit)
    }
}

/** The draft cover (first frame) or a placeholder. */
@Composable
internal fun Cover(path: String?, modifier: Modifier, radius: Int = 12) {
    Box(modifier.clip(RoundedCornerShape(radius.dp)).background(ClipBackground)) {
        path?.let { AsyncImage(model = File(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

private fun ExportFailure.messageRes(): Int = when (this) {
    ExportFailure.PROJECT_NOT_FOUND -> R.string.editor_not_found
    ExportFailure.PERMISSION_DENIED -> R.string.export_error_permission
    ExportFailure.EXPORT_FAILED -> R.string.export_error_encode
    ExportFailure.SAVE_FAILED -> R.string.export_error_save
}
