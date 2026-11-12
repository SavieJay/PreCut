package com.savie.precut.ui.editor.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.theme.TextMuted
import kotlin.math.roundToInt

/** Motion blur (CapCut Pro): strength 0–100%, applied on release, with Reset and Apply to all. */
@Composable
fun MotionBlurTool(strength: Float, onChanged: (Float) -> Unit, onApplyAll: () -> Unit, onDone: () -> Unit) {
    var value by remember(strength) { mutableFloatStateOf(strength) }
    ToolPanel(
        stringResource(R.string.tool_motion_blur),
        onDone,
        actions = {
            ResetIconButton({ onChanged(0f) }, enabled = strength > 0f, description = stringResource(R.string.action_reset))
            ApplyToAllButton(onApplyAll)
        },
    ) {
        SliderRow(value = "${(value * 100).roundToInt()}%", label = stringResource(R.string.motion_blur_strength)) { sliderModifier ->
            PreCutSlider(
                value = value,
                onValueChange = { value = (it * 100).roundToInt() / 100f },
                onValueChangeFinished = { onChanged(value) },
                bubble = { "${(it * 100).roundToInt()}%" },
                onReset = { onChanged(0f) },
                modifier = sliderModifier,
            )
        }
        Text(stringResource(R.string.motion_blur_hint), style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}
