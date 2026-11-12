package com.savie.precut.ui.editor.export

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.ai.edit.EditCatalog
import com.savie.precut.ui.components.AiTag
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutProgressBar
import com.savie.precut.ui.editor.FormatChoice
import com.savie.precut.ui.editor.FormatsUi
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.GlassSolid
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glassRow

/** Multi-format export (E3): the three shapes side by side, each ticked, then Export N videos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatsSheet(ui: FormatsUi, onToggle: (AspectRatio) -> Unit, onExport: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassSolid,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.formats_title), style = PreCutType.panelTitle, color = TextPrimary)
                AiTag(stringResource(R.string.rd_media_ai_tag))
            }
            val percent = ui.percent
            if (percent != null) {
                Text(stringResource(R.string.formats_tracking, percent), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                PreCutProgressBar(percent / 100f, Modifier.fillMaxWidth(), ai = true)
                return@Column
            }
            Text(stringResource(R.string.formats_body), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                ui.choices.forEach { choice -> FormatCard(choice, { onToggle(choice.ratio) }, Modifier.weight(1f)) }
            }
            val count = ui.choices.count { it.selected }
            FilledButton(pluralStringResource(R.plurals.formats_export, count, count), onExport, Modifier.fillMaxWidth(), large = true, enabled = count > 0)
        }
    }
}

@Composable
private fun FormatCard(choice: FormatChoice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ratio = choice.ratio.widthRatio.toFloat() / choice.ratio.heightRatio
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).glassRow(Radius.tile)
                .then(if (choice.selected) Modifier.border(3.dp, Accent, Radius.tile) else Modifier),
        ) {
            choice.preview?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(Radius.tile)) }
            if (choice.selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = OnAccent, modifier = Modifier.size(15.dp))
                }
            }
        }
        Text(EditCatalog.ratioName(choice.ratio), style = MaterialTheme.typography.labelLarge, color = if (choice.selected) TextPrimary else TextSecondary)
    }
}
