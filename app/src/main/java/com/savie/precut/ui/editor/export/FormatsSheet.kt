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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.ai.edit.EditCatalog
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.editor.FormatChoice
import com.savie.precut.ui.editor.FormatsUi
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.TextSecondary

/** Multi-format export (E3): the three shapes side by side, each ticked, then Export N videos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatsSheet(ui: FormatsUi, onToggle: (AspectRatio) -> Unit, onExport: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink1,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.formats_title), style = MaterialTheme.typography.titleMedium)
            val percent = ui.percent
            if (percent != null) {
                Text(stringResource(R.string.formats_tracking, percent), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Accent,
                    trackColor = Ink3,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                return@Column
            }
            Text(stringResource(R.string.formats_body), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
            Modifier.fillMaxWidth().aspectRatio(ratio).background(Ink2, RoundedCornerShape(8.dp))
                .border(if (choice.selected) 2.dp else 1.dp, if (choice.selected) Accent else Outline, RoundedCornerShape(8.dp)),
        ) {
            choice.preview?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(ratio)) }
            if (choice.selected) {
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = Accent, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp))
            }
        }
        Text(EditCatalog.ratioName(choice.ratio), style = MaterialTheme.typography.labelLarge)
    }
}
