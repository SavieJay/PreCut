package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.formatPreciseMs
import kotlin.math.roundToLong

private const val MAX_FADE_MS = 10_000L

/** Fade-in and fade-out sliders for a clip's sound (0 to min(10 s, clip length)); applied on release. */
@Composable
fun FadeTool(
    fadeInMs: Long,
    fadeOutMs: Long,
    lengthMs: Long,
    onFadesChanged: (fadeInMs: Long, fadeOutMs: Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = minOf(MAX_FADE_MS, lengthMs).coerceAtLeast(1L).toFloat()
    var fadeIn by remember(fadeInMs) { mutableFloatStateOf(fadeInMs.toFloat()) }
    var fadeOut by remember(fadeOutMs) { mutableFloatStateOf(fadeOutMs.toFloat()) }
    val commit = { onFadesChanged(fadeIn.roundToLong(), fadeOut.roundToLong()) }
    ToolPanel(stringResource(R.string.fade_title), onDone, modifier) {
        FadeRow(stringResource(R.string.fade_in), fadeIn, max, { fadeIn = it }, commit)
        FadeRow(stringResource(R.string.fade_out), fadeOut, max, { fadeOut = it }, commit)
    }
}

@Composable
private fun FadeRow(label: String, value: Float, max: Float, onChange: (Float) -> Unit, onCommit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
        Slider(
            value = value.coerceAtMost(max),
            onValueChange = { onChange((it / 100).roundToLong() * 100f) },
            onValueChangeFinished = onCommit,
            valueRange = 0f..max,
            modifier = Modifier.weight(1f),
        )
        Text(formatPreciseMs(value.roundToLong()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
    }
}
