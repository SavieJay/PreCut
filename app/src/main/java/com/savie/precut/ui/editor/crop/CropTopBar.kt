package com.savie.precut.ui.editor.crop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentPill
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.theme.TextMuted
import androidx.compose.material3.MaterialTheme

/** Crop mode's 56 dp top bar (RD-T12b): ✕ cancels, the title with a hint, the pink Apply pill applies. */
@Composable
fun CropTopBar(onCancel: () -> Unit, onApply: () -> Unit) {
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PreCutIconButton(R.drawable.ic_close, stringResource(R.string.crop_cancel), onCancel)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                stringResource(R.string.crop_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.rd_clip_crop_hint), style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1)
        }
        AccentPill(stringResource(R.string.rd_clip_crop_apply), enabled = true, onClick = onApply)
    }
}
