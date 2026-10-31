package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.audio.NoiseReduction
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.components.SwitchRow
import kotlin.math.roundToInt

/** What the Reduce noise rows report. */
interface NoiseActions {
    /** On measures the noise first (with progress); off plays the original sound. */
    fun onNoiseReduction(enabled: Boolean)
    fun onNoiseStrength(strength: Float)
}

/** Reduce noise (Phase 22B) in a Volume panel: the switch, then Strength (applied on release). */
@Composable
fun NoiseReductionRows(reduction: NoiseReduction?, actions: NoiseActions) {
    SwitchRow(
        stringResource(R.string.noise_reduce),
        checked = reduction != null,
        onCheckedChange = actions::onNoiseReduction,
        description = if (reduction == null) stringResource(R.string.noise_intro) else null,
    )
    if (reduction == null) return
    var shown by remember(reduction.strength) { mutableFloatStateOf(reduction.strength) }
    SliderRow(value = "${(shown * 100).roundToInt()}", label = stringResource(R.string.noise_strength)) { sliderModifier ->
        PreCutSlider(
            value = shown,
            onValueChange = { shown = it },
            onValueChangeFinished = { actions.onNoiseStrength(shown) },
            onReset = { actions.onNoiseStrength(NoiseReduction.DEFAULT_STRENGTH) },
            modifier = sliderModifier,
        )
    }
}
