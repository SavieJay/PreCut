package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.theme.BeatDot
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/**
 * U-04: one big button adds a beat at the playhead, or removes the one there (works while playing).
 * Text and audio bars snap to beats while dragged.
 */
@Composable
fun BeatsTool(
    beatCount: Int,
    beatAtPlayhead: Boolean,
    playheadMs: Long,
    onToggleBeat: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(
        stringResource(R.string.beats_title, beatCount),
        onDone,
        modifier,
        actions = { AccentTextButton(stringResource(R.string.beats_clear), onClear, enabled = beatCount > 0, color = TextSecondary) },
    ) {
        Button(
            onClick = onToggleBeat,
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
        Text(
            stringResource(R.string.beats_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
