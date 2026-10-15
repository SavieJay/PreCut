package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.ProjectCover
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.OutlinedPreCutButton
import com.savie.precut.ui.components.SwitchRow
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.editor.timeline.ThumbnailTile
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/** Callbacks of the Cover panel. */
interface CoverActions {
    fun onUseFrame()
    fun onPickImage()
    fun onReset()
    fun onInExportChanged(inExport: Boolean)
}

/**
 * T-11 (CapCut's Cover): the current cover beside "Use frame" (the frame under the playhead) and
 * "From gallery"; optionally shown as the video's first frame so apps use it as the thumbnail.
 */
@Composable
fun CoverTool(
    cover: ProjectCover?,
    inExport: Boolean,
    playheadMs: Long,
    coverKey: ThumbnailKey?,
    loader: ThumbnailLoader,
    actions: CoverActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(
        stringResource(R.string.cover_title),
        onDone,
        modifier,
        actions = { AccentTextButton(stringResource(R.string.cover_reset), actions::onReset, enabled = cover != null, color = TextSecondary) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(8.dp)
            coverKey?.let { key ->
                ThumbnailTile(loader, key, modifier = Modifier.width(72.dp).height(128.dp).clip(shape).border(2.dp, Accent, shape))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (cover) {
                        null -> stringResource(R.string.cover_current_default)
                        is ProjectCover.Frame -> stringResource(R.string.cover_current_frame, formatPreciseMs(cover.timelineMs))
                        is ProjectCover.Image -> stringResource(R.string.cover_current_photo)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp).tabular(),
                )
                TonalButton(stringResource(R.string.cover_use_frame, formatPreciseMs(playheadMs)), actions::onUseFrame, icon = R.drawable.ic_add_photo_alternate)
                OutlinedPreCutButton(stringResource(R.string.cover_from_gallery), actions::onPickImage, icon = R.drawable.ic_gallery)
            }
        }
        SwitchRow(
            label = stringResource(R.string.cover_in_export),
            description = stringResource(R.string.cover_in_export_detail),
            checked = inExport,
            enabled = cover != null,
            onCheckedChange = actions::onInExportChanged,
        )
    }
}
