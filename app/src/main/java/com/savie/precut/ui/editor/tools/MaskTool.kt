package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.frame.MaskShape
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.PresetTile
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** What the Mask panel reports. */
interface MaskActions {
    /** A shape, or null for None. */
    fun onShape(shape: MaskShape?)
    fun onFeatherLive(feather: Float)
    fun onFeatherDone(feather: Float)
    fun onCorners(radius: Float)
    fun onInvert()
    fun onReset()
}

/** Mask (P-03): shape row, Feather and Invert; size, place and turn on the preview. */
@Composable
fun MaskTool(mask: ClipMask?, actions: MaskActions, onDone: () -> Unit) {
    ToolPanel(
        stringResource(R.string.tool_mask),
        onDone,
        actions = { AccentTextButton(stringResource(R.string.action_reset), onClick = actions::onReset, enabled = mask != null, color = TextSecondary) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PresetTile(stringResource(R.string.mask_none), R.drawable.ic_block, selected = mask == null, onClick = { actions.onShape(null) })
                MaskShape.entries.forEach { shape ->
                    PresetTile(stringResource(shape.label()), shape.icon(), selected = mask?.shape == shape, onClick = { actions.onShape(shape) })
                }
            }
            if (mask != null) {
                var feather by remember(mask.feather) { mutableFloatStateOf(mask.feather) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SliderRow(
                        value = "${(feather * 100).roundToInt()}",
                        label = stringResource(R.string.mask_feather),
                        modifier = Modifier.weight(1f),
                    ) { sliderModifier ->
                        PreCutSlider(
                            value = feather,
                            onValueChange = {
                                feather = it
                                actions.onFeatherLive(it)
                            },
                            onValueChangeFinished = { actions.onFeatherDone(feather) },
                            onReset = { actions.onFeatherDone(0f) },
                            modifier = sliderModifier,
                        )
                    }
                    IconToggleButton(checked = mask.inverted, onCheckedChange = { actions.onInvert() }) {
                        Icon(
                            painterResource(R.drawable.ic_invert),
                            contentDescription = stringResource(R.string.mask_invert),
                            tint = if (mask.inverted) Accent else TextSecondary,
                        )
                    }
                }
                if (mask.shape == MaskShape.RECTANGLE) {
                    var corners by remember(mask.cornerRadius) { mutableFloatStateOf(mask.cornerRadius) }
                    SliderRow(value = "${(corners * 100).roundToInt()}", label = stringResource(R.string.mask_corners)) { sliderModifier ->
                        PreCutSlider(
                            value = corners,
                            onValueChange = { corners = it },
                            onValueChangeFinished = { actions.onCorners(corners) },
                            onReset = { actions.onCorners(0f) },
                            modifier = sliderModifier,
                        )
                    }
                }
            }
        }
    }
}

private fun MaskShape.label(): Int = when (this) {
    MaskShape.LINEAR -> R.string.mask_linear
    MaskShape.MIRROR -> R.string.mask_mirror
    MaskShape.CIRCLE -> R.string.mask_circle
    MaskShape.RECTANGLE -> R.string.mask_rectangle
    MaskShape.HEART -> R.string.mask_heart
    MaskShape.STAR -> R.string.mask_star
}

private fun MaskShape.icon(): Int = when (this) {
    MaskShape.LINEAR -> R.drawable.ic_horizontal_rule
    MaskShape.MIRROR -> R.drawable.ic_drag_handle
    MaskShape.CIRCLE -> R.drawable.ic_circle
    MaskShape.RECTANGLE -> R.drawable.ic_rectangle
    MaskShape.HEART -> R.drawable.ic_favorite
    MaskShape.STAR -> R.drawable.ic_star
}
