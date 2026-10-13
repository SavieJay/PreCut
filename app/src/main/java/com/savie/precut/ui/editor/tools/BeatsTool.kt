package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R

/**
 * Manual beat markers: tap Add/Remove while the playhead sits on a beat (works during playback).
 * Text and audio bars snap to beats while dragged.
 */
@Composable
fun BeatsTool(
    beatCount: Int,
    beatAtPlayhead: Boolean,
    onToggleBeat: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(stringResource(R.string.beats_title), onDone, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onToggleBeat) {
                Text(stringResource(if (beatAtPlayhead) R.string.beats_remove else R.string.beats_add))
            }
            TextButton(onClick = onClear, enabled = beatCount > 0) { Text(stringResource(R.string.beats_clear)) }
            Text(
                pluralStringResource(R.plurals.beats_count, beatCount, beatCount),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
