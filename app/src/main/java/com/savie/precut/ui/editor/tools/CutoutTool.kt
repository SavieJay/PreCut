package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.savie.precut.model.frame.Cutout
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.components.SwitchRow
import com.savie.precut.ui.theme.ErrorRed
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** What the Remove background panel reports. */
interface CutoutActions {
    fun onEnabled(enabled: Boolean)
    fun onUpdate()
    fun onSoftness(softness: Float)
    fun onEdge(edge: Float)
    fun onShowOriginal()
}

/**
 * Remove background (Phase 22A): the switch (on works out the matte, with progress), then
 * Softness, Edge and Show original. [coversClip] false offers Update, after a longer trim or a
 * Reverse. Values apply on release.
 */
@Composable
fun CutoutTool(cutout: Cutout?, coversClip: Boolean, showingOriginal: Boolean, actions: CutoutActions, onDone: () -> Unit) {
    ToolPanel(stringResource(R.string.cutout_title), onDone) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchRow(
                stringResource(R.string.cutout_switch),
                checked = cutout != null,
                onCheckedChange = actions::onEnabled,
                description = stringResource(if (cutout == null) R.string.cutout_intro else R.string.cutout_limits),
            )
            if (cutout != null) {
                if (!coversClip) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.cutout_update_needed), style = PreCutType.labelChip, color = ErrorRed, modifier = Modifier.weight(1f))
                        AccentTextButton(stringResource(R.string.cutout_update), onClick = actions::onUpdate)
                    }
                }
                CutoutSlider(R.string.cutout_softness, cutout.softness, 0f..1f, anchor = null, onChanged = actions::onSoftness)
                CutoutSlider(R.string.cutout_edge, cutout.edge, -1f..1f, anchor = 0f, onChanged = actions::onEdge)
                SwitchRow(stringResource(R.string.cutout_show_original), checked = showingOriginal, onCheckedChange = { actions.onShowOriginal() })
            } else {
                Text(stringResource(R.string.cutout_limits), style = PreCutType.labelChip, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun CutoutSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, anchor: Float?, onChanged: (Float) -> Unit) {
    var shown by remember(value) { mutableFloatStateOf(value) }
    SliderRow(value = "${(shown * 100).roundToInt()}", label = stringResource(label)) { sliderModifier ->
        PreCutSlider(
            value = shown,
            onValueChange = { shown = it },
            valueRange = range,
            onValueChangeFinished = { onChanged(shown) },
            anchor = anchor,
            onReset = { onChanged(anchor ?: Cutout.DEFAULT_SOFTNESS) },
            modifier = sliderModifier,
        )
    }
}
