package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.edit.MAX_SPEED
import com.savie.precut.model.edit.MIN_SPEED
import java.util.Locale
import kotlin.math.roundToInt

private val PRESETS = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 3f, 4f)

/** Speed presets plus a fine slider (0.25x steps); applied on chip tap or slider release. */
@Composable
fun SpeedTool(
    speed: Float,
    onSpeedChanged: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(speed) { mutableFloatStateOf(speed) }
    ToolPanel(stringResource(R.string.speed_title, formatSpeed(sliderValue)), onDone, modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { preset ->
                FilterChip(
                    selected = preset == speed,
                    onClick = { onSpeedChanged(preset) },
                    label = { Text(stringResource(R.string.speed_value, formatSpeed(preset))) },
                )
            }
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = (it * 4).roundToInt() / 4f },
            onValueChangeFinished = { onSpeedChanged(sliderValue) },
            valueRange = MIN_SPEED..MAX_SPEED,
        )
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else String.format(Locale.ROOT, "%.2f", speed).trimEnd('0')
