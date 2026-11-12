package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AiChip
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.PreCutSpinner
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.theme.TextMuted

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
        actions = { PreCutIconButton(R.drawable.ic_delete_sweep, stringResource(R.string.beats_clear), actions::onClear, small = true, enabled = beatCount > 0) },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiChip(
                stringResource(if (detecting) R.string.beats_detecting else R.string.beats_detect),
                { actions.onDetect(every) },
                icon = R.drawable.ic_beats,
                enabled = !detecting,
            )
            if (detecting) PreCutSpinner(size = 20.dp, ai = true)
            DENSITIES.forEach { (value, label) -> PreCutChip(stringResource(label), selected = every == value, onClick = { every = value }) }
        }
        ToggleBeatButton(beatAtPlayhead, playheadMs, actions::onToggleBeat)
        if (beatCount > 0) {
            TonalButton(stringResource(R.string.beats_cut_clips), actions::onCutToBeats, Modifier.fillMaxWidth(), icon = R.drawable.ic_split)
        }
        Text(
            stringResource(R.string.beats_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The panel's one pink action (RD-U04): add or remove the beat at the playhead. */
@Composable
private fun ToggleBeatButton(beatAtPlayhead: Boolean, playheadMs: Long, onClick: () -> Unit) {
    FilledButton(
        stringResource(if (beatAtPlayhead) R.string.beats_remove else R.string.beats_add, formatPreciseMs(playheadMs)),
        onClick,
        Modifier.fillMaxWidth(),
        icon = if (beatAtPlayhead) R.drawable.ic_remove_circle else R.drawable.ic_add_circle,
        large = true,
    )
}

/** Every beat, every 2nd, every 4th (CapCut's beat densities). */
private val DENSITIES = listOf(1 to R.string.beats_every_1, 2 to R.string.beats_every_2, 4 to R.string.beats_every_4)
