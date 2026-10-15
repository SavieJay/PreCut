package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.MAX_VOLUME
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import kotlin.math.roundToInt

/** 0–200% volume in 5% steps with a mark at 100% (T-04); applied on release, double-tap resets. */
@Composable
fun VolumeSlider(volume: Float, enabled: Boolean, onVolumeChanged: (Float) -> Unit, modifier: Modifier = Modifier) {
    var value by remember(volume) { mutableFloatStateOf(volume) }
    val label = stringResource(R.string.volume_percent, (value * 100).roundToInt())
    SliderRow(value = label, icon = R.drawable.ic_volume, modifier = modifier) { sliderModifier ->
        PreCutSlider(
            value = value,
            onValueChange = { value = (it * 20).roundToInt() / 20f },
            onValueChangeFinished = { onVolumeChanged(value) },
            valueRange = 0f..MAX_VOLUME,
            anchor = 1f,
            onReset = { onVolumeChanged(1f) },
            enabled = enabled,
            snapTo = listOf(1f),
            modifier = sliderModifier,
        )
    }
}
