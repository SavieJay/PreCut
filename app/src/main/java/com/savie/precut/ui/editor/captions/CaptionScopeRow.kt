package com.savie.precut.ui.editor.captions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.theme.TextSecondary

/** Text style on a caption (Phase 21B): whether changes go to this caption or to all of them. */
@Composable
fun CaptionScopeRow(all: Boolean, onScope: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.captions_scope_label), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        PreCutChip(stringResource(R.string.captions_scope_all), selected = all, onClick = { onScope(true) })
        PreCutChip(stringResource(R.string.captions_scope_this), selected = !all, onClick = { onScope(false) })
    }
}
