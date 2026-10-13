package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.savie.precut.model.SpeedScale
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.components.formatSpeed
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToLong

/** Presets for clips (CapCut's 0.1x–100x) and for audio clips. */
val CLIP_SPEED_PRESETS = listOf(0.1f, 0.5f, 1f, 2f, 5f, 10f, 100f)
val AUDIO_SPEED_PRESETS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f)

/**
 * Speed presets plus a logarithmic slider over [scale]; applied on chip tap or slider release.
 * Shows the new length when [sourceLengthMs] is given, and a "Keep pitch" switch when
 * [keepPitch] is non-null.
 */
@Composable
fun SpeedTool(
    speed: Float,
    onSpeedChanged: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    scale: SpeedScale = SpeedScale.CLIP,
    presets: List<Float> = CLIP_SPEED_PRESETS,
    sourceLengthMs: Long? = null,
    keepPitch: Boolean? = null,
    onKeepPitchChanged: (Boolean) -> Unit = {},
) {
    var position by remember(speed) { mutableFloatStateOf(scale.toSlider(speed)) }
    val shown = scale.fromSlider(position)
    ToolPanel(stringResource(R.string.speed_title, formatSpeed(shown)), onDone, modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                FilterChip(
                    selected = preset == speed,
                    onClick = { onSpeedChanged(preset) },
                    label = { Text(stringResource(R.string.speed_value, formatSpeed(preset))) },
                )
            }
        }
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onSpeedChanged(scale.fromSlider(position)) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            sourceLengthMs?.let { length ->
                Text(
                    stringResource(R.string.speed_length, formatSeconds(length), formatSeconds((length / shown.toDouble()).roundToLong())),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            keepPitch?.let { checked ->
                Text(stringResource(R.string.speed_keep_pitch), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Switch(checked = checked, onCheckedChange = onKeepPitchChanged)
            }
        }
    }
}
