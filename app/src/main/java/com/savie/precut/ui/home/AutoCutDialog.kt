package com.savie.precut.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.TextSecondary

/** AutoCut: how often to cut, then clips and a song are picked; rule-based, so it says what it does. */
@Composable
fun AutoCutDialog(every: Int, onEvery: (Int) -> Unit, onChooseClips: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink1,
        title = { Text(stringResource(R.string.autocut_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.autocut_body), color = TextSecondary)
                Text(stringResource(R.string.autocut_pace))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PACES.forEach { (value, label) -> PreCutChip(stringResource(label), selected = every == value, onClick = { onEvery(value) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onChooseClips) { Text(stringResource(R.string.autocut_choose_clips)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val PACES = listOf(1 to R.string.beats_every_1, 2 to R.string.beats_every_2, 4 to R.string.beats_every_4)
