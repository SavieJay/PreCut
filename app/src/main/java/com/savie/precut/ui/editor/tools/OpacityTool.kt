package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** Opacity (T-14): 0–100% in 1% steps; the preview follows while dragging, the edit lands on release. */
@Composable
fun OpacityTool(opacity: Float, onLive: (Float) -> Unit, onChanged: (Float) -> Unit, onDone: () -> Unit) {
    var value by remember(opacity) { mutableFloatStateOf(opacity) }
    ToolPanel(
        stringResource(R.string.tool_opacity),
        onDone,
        actions = { AccentTextButton(stringResource(R.string.action_reset), onClick = { onChanged(1f) }, enabled = opacity < 1f, color = TextSecondary) },
    ) {
        SliderRow(value = "${(value * 100).roundToInt()}%", icon = R.drawable.ic_opacity) { sliderModifier ->
            PreCutSlider(
                value = value,
                onValueChange = {
                    value = (it * 100).roundToInt() / 100f
                    onLive(value)
                },
                onValueChangeFinished = { onChanged(value) },
                onReset = { onChanged(1f) },
                modifier = sliderModifier,
            )
        }
    }
}
