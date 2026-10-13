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
import com.savie.precut.model.MAX_VOLUME
import kotlin.math.roundToInt

/** 0–200% volume slider in 5% steps; applied on release. */
@Composable
fun VolumeSlider(volume: Float, enabled: Boolean, onVolumeChanged: (Float) -> Unit, modifier: Modifier = Modifier) {
    var value by remember(volume) { mutableFloatStateOf(volume) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = { value = (it * 20).roundToInt() / 20f },
            onValueChangeFinished = { onVolumeChanged(value) },
            valueRange = 0f..MAX_VOLUME,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.volume_percent, (value * 100).roundToInt()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(48.dp),
        )
    }
}
