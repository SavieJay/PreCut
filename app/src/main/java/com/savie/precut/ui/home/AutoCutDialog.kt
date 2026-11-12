package com.savie.precut.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextSecondary

/** AutoCut: how often to cut, then clips and a song are picked; rule-based, so it says what it does. */
@Composable
fun AutoCutDialog(every: Int, onEvery: (Int) -> Unit, onChooseClips: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(title = stringResource(R.string.autocut_title), onDismiss = onDismiss) {
        Text(stringResource(R.string.autocut_body), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.autocut_pace).uppercase(), style = PreCutType.section, color = TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PACES.forEach { (value, label) -> PreCutChip(stringResource(label), selected = every == value, onClick = { onEvery(value) }) }
            }
        }
        FilledButton(
            text = stringResource(R.string.autocut_choose_clips),
            onClick = onChooseClips,
            large = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val PACES = listOf(1 to R.string.beats_every_1, 2 to R.string.beats_every_2, 4 to R.string.beats_every_4)
