package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.OutlinedPreCutButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.theme.BeatDot
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

interface BeatsActions {
    fun onToggleBeat()
    fun onClear()
    /** Find the beats and keep every [every]th. */
    fun onDetect(every: Int)
    fun onStopDetect()
    fun onCutToBeats()
    fun onDone()
}

/**
 * U-04: Detect finds the song's beats (every beat, 2nd or 4th); the big button adds or removes one
 * at the playhead (works while playing); Cut clips to the beat ends each main-track clip on the
 * next beat. Text and audio bars snap to beats while dragged.
 */
@Composable
fun BeatsTool(beatCount: Int, beatAtPlayhead: Boolean, playheadMs: Long, detecting: Boolean, actions: BeatsActions, modifier: Modifier = Modifier) {
    var every by rememberSaveable { mutableIntStateOf(1) }
    // A detection stops when the panel closes.
    DisposableEffect(actions) { onDispose { actions.onStopDetect() } }
    ToolPanel(
        stringResource(R.string.beats_title, beatCount),
        actions::onDone,
        modifier,
        actions = { AccentTextButton(stringResource(R.string.beats_clear), actions::onClear, enabled = beatCount > 0, color = TextSecondary) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DENSITIES.forEach { (value, label) -> PreCutChip(stringResource(label), selected = every == value, onClick = { every = value }) }
            Spacer(Modifier.weight(1f))
            if (detecting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            FilledButton(stringResource(if (detecting) R.string.beats_detecting else R.string.beats_detect), { actions.onDetect(every) }, enabled = !detecting)
        }
        ToggleBeatButton(beatAtPlayhead, playheadMs, actions::onToggleBeat)
        if (beatCount > 0) {
            OutlinedPreCutButton(stringResource(R.string.beats_cut_clips), actions::onCutToBeats, Modifier.fillMaxWidth(), icon = R.drawable.ic_split)
        }
        Text(
            stringResource(R.string.beats_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToggleBeatButton(beatAtPlayhead: Boolean, playheadMs: Long, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Ink3, contentColor = TextPrimary),
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painterResource(if (beatAtPlayhead) R.drawable.ic_remove_circle else R.drawable.ic_add_circle),
                contentDescription = null,
                tint = BeatDot,
                modifier = Modifier.size(24.dp),
            )
            Text(
                stringResource(if (beatAtPlayhead) R.string.beats_remove else R.string.beats_add, formatPreciseMs(playheadMs)),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp).tabular(),
            )
        }
    }
}

/** Every beat, every 2nd, every 4th (CapCut's beat densities). */
private val DENSITIES = listOf(1 to R.string.beats_every_1, 2 to R.string.beats_every_2, 4 to R.string.beats_every_4)
