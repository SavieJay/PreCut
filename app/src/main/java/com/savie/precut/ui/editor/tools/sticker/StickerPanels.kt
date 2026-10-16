package com.savie.precut.ui.editor.tools.sticker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.TextAnimationType
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.editor.tools.ToolPanel
import com.savie.precut.ui.editor.tools.text.TextAnimationTab
import kotlin.math.roundToInt

/** In, Out and Loop animations for a sticker: the text ones without Typewriter. */
@Composable
fun StickerAnimationTool(sticker: TextOverlay, onChange: (TextOverlay) -> Unit, onDone: () -> Unit) {
    ToolPanel(stringResource(R.string.tool_animation), onDone) {
        TextAnimationTab(sticker, onChange, types = TextAnimationType.entries - TextAnimationType.TYPEWRITER)
    }
}

/** The sticker's opacity, applied on release. */
@Composable
fun StickerOpacityTool(opacity: Float, onOpacity: (Float) -> Unit, onDone: () -> Unit) {
    var value by remember(opacity) { mutableFloatStateOf(opacity) }
    ToolPanel(stringResource(R.string.tool_opacity), onDone) {
        SliderRow(value = "${(value * 100).roundToInt()}%", label = stringResource(R.string.tool_opacity)) { sliderModifier ->
            PreCutSlider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { onOpacity(value) },
                valueRange = 0.1f..1f,
                onReset = { onOpacity(1f) },
                modifier = sliderModifier,
            )
        }
    }
}
