package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import kotlin.math.roundToInt

/** Opacity (RD-T14): 0–100% in 1% steps; the preview follows while dragging, the edit lands on release. */
@Composable
fun OpacityTool(opacity: Float, onLive: (Float) -> Unit, onChanged: (Float) -> Unit, onDone: () -> Unit) {
    var value by remember(opacity) { mutableFloatStateOf(opacity) }
    val percent = { v: Float -> "${(v * 100).roundToInt()}%" }
    ToolPanel(
        stringResource(R.string.tool_opacity),
        onDone,
        actions = { ResetIconButton({ onChanged(1f) }, enabled = opacity < 1f, description = stringResource(R.string.action_reset)) },
    ) {
        SliderRow(value = percent(value), icon = R.drawable.ic_opacity, valueWidth = 44.dp, modifier = Modifier) { sliderModifier ->
            PreCutSlider(
                value = value,
                onValueChange = {
                    value = (it * 100).roundToInt() / 100f
                    onLive(value)
                },
                onValueChangeFinished = { onChanged(value) },
                bubble = percent,
                onReset = { onChanged(1f) },
                knobSize = 28.dp,
                modifier = sliderModifier,
            )
        }
    }
}
