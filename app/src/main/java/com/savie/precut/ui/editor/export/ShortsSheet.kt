package com.savie.precut.ui.editor.export

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.AiTag
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutProgressBar
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.editor.ShortDraft
import com.savie.precut.ui.editor.ShortsUi
import com.savie.precut.ui.theme.GlassSolid
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glassRow

/** What the shorts sheet can do. */
interface ShortsActions {
    fun onOpen(projectId: String)
    fun onExport(projectId: String)
    fun onExportAll()
    fun onDismiss()
}

/** Long video to shorts (E2): progress, then each short with its frame, length, title and hook score. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsSheet(ui: ShortsUi, actions: ShortsActions) {
    ModalBottomSheet(
        onDismissRequest = actions::onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassSolid,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.shorts_title), style = PreCutType.panelTitle, color = TextPrimary)
                AiTag(stringResource(R.string.rd_media_ai_tag))
            }
            when (ui) {
                is ShortsUi.Working -> {
                    Text(
                        stringResource(if (ui.making) R.string.shorts_making else R.string.shorts_finding, ui.percent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    PreCutProgressBar(ui.percent / 100f, Modifier.fillMaxWidth(), ai = true)
                }
                is ShortsUi.Results -> {
                    Text(stringResource(R.string.shorts_saved), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ui.shorts, key = { it.projectId }) { short -> ShortRow(short, actions) }
                    }
                    FilledButton(stringResource(R.string.shorts_export_all), actions::onExportAll, Modifier.fillMaxWidth(), large = true)
                }
            }
        }
    }
}

@Composable
private fun ShortRow(short: ShortDraft, actions: ShortsActions) {
    Row(Modifier.fillMaxWidth().glassRow(Radius.card).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(64.dp).aspectRatio(9f / 16f).clip(Radius.clip).background(Ink3)) {
            short.thumbnail?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(short.title.ifBlank { stringResource(R.string.shorts_untitled) }, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(formatDurationMs(short.durationMs), style = PreCutType.timecode, color = TextMuted)
            AiTag(stringResource(R.string.shorts_hook_score, short.hookScore))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentTextButton(stringResource(R.string.shorts_open), { actions.onOpen(short.projectId) })
                AccentTextButton(stringResource(R.string.shorts_export), { actions.onExport(short.projectId) })
            }
        }
    }
}
