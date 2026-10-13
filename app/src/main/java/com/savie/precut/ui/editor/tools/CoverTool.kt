package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.ProjectCover
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.theme.TextSecondary

/** Callbacks of the Cover panel. */
interface CoverActions {
    fun onUseFrame()
    fun onPickImage()
    fun onReset()
    fun onInExportChanged(inExport: Boolean)
}

/**
 * CapCut's Cover: scrub the timeline and use the frame under the playhead, or pick a photo.
 * Optionally shown as the video's first frame so apps use it as the thumbnail.
 */
@Composable
fun CoverTool(
    cover: ProjectCover?,
    inExport: Boolean,
    playheadMs: Long,
    actions: CoverActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(stringResource(R.string.cover_title), onDone, modifier) {
        Text(
            when (cover) {
                null -> stringResource(R.string.cover_current_default)
                is ProjectCover.Frame -> stringResource(R.string.cover_current_frame, formatPreciseMs(cover.timelineMs))
                is ProjectCover.Image -> stringResource(R.string.cover_current_photo)
            },
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = actions::onUseFrame) {
                Text(stringResource(R.string.cover_use_frame, formatPreciseMs(playheadMs)))
            }
            OutlinedButton(onClick = actions::onPickImage) { Text(stringResource(R.string.cover_from_gallery)) }
            if (cover != null) TextButton(onClick = actions::onReset) { Text(stringResource(R.string.cover_reset)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cover_in_export), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Switch(checked = inExport, enabled = cover != null, onCheckedChange = actions::onInExportChanged)
        }
    }
}
