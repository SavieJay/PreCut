package com.savie.precut.ui.editor.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToLong

private const val MAX_FADE_MS = 10_000L

/** T-05: fade-in and fade-out of a clip's sound, each up to half its length (max 10 s); applied on release. */
@Composable
fun FadeTool(
    fadeInMs: Long,
    fadeOutMs: Long,
    lengthMs: Long,
    onFadesChanged: (fadeInMs: Long, fadeOutMs: Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = minOf(MAX_FADE_MS, lengthMs / 2).coerceAtLeast(100L)
    var fadeIn by remember(fadeInMs) { mutableFloatStateOf(fadeInMs.toFloat()) }
    var fadeOut by remember(fadeOutMs) { mutableFloatStateOf(fadeOutMs.toFloat()) }
    val commit = { onFadesChanged(fadeIn.roundToLong(), fadeOut.roundToLong()) }
    ToolPanel(stringResource(R.string.fade_title), onDone, modifier) {
        FadeRow(stringResource(R.string.fade_in), fadeIn, max.toFloat(), { fadeIn = it }, commit)
        FadeRow(stringResource(R.string.fade_out), fadeOut, max.toFloat(), { fadeOut = it }, commit)
        Text(stringResource(R.string.fade_limit, formatSeconds(max)), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun FadeRow(label: String, value: Float, max: Float, onChange: (Float) -> Unit, onCommit: () -> Unit) {
    SliderRow(value = formatSeconds(value.roundToLong()), label = label) { modifier ->
        PreCutSlider(
            value = value.coerceAtMost(max),
            onValueChange = { onChange((it / 100).roundToLong() * 100f) },
            onValueChangeFinished = onCommit,
            valueRange = 0f..max,
            modifier = modifier,
        )
    }
}
