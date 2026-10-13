package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.look.CurveChannel
import com.savie.precut.model.look.CurvePoint
import com.savie.precut.model.look.Curves
import com.savie.precut.ui.theme.TextSecondary

/** CapCut Pro's Curves: an RGB master curve and one per channel. */
@Composable
fun CurvesPanel(curves: Curves, onCurve: (CurveChannel, List<CurvePoint>) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var channel by remember { mutableStateOf(CurveChannel.RGB) }
    ToolPanel(
        stringResource(R.string.curves_title),
        onDone,
        modifier,
        actions = {
            TextButton(onClick = { onCurve(channel, Curves.IDENTITY) }) { Text(stringResource(R.string.adjust_reset)) }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CurveChannel.entries.forEach { c ->
                FilterChip(selected = c == channel, onClick = { channel = c }, label = { Text(stringResource(c.labelRes())) })
            }
        }
        CurveEditor(points = curves[channel], channel = channel, onPoints = { onCurve(channel, it) })
        Text(stringResource(R.string.curves_hint), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
