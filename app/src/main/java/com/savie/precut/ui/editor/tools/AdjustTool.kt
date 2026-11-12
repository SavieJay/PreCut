package com.savie.precut.ui.editor.tools

import androidx.annotation.DrawableRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.look.Adjustment
import com.savie.precut.model.look.ClipLook
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextMuted
import kotlin.math.roundToInt

/** What the Adjust panel can do. */
interface AdjustActions {
    fun onAdjust(adjustment: Adjustment, value: Float)
    fun onReset()
    fun onHsl()
    fun onCurves()
}

/**
 * T-07: chips pick the property one slider edits; changed chips show their value. Bipolar
 * properties snap to 0 and double-tap resets; HSL and Curves open their own panels. Applies on
 * release.
 */
@Composable
fun AdjustTool(
    look: ClipLook,
    actions: AdjustActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onApplyAll: (() -> Unit)? = null,
) {
    var selected by remember { mutableStateOf(Adjustment.BRIGHTNESS) }
    var value by remember(selected, look.adjust[selected]) { mutableFloatStateOf(look.adjust[selected]) }
    val edited = !look.adjust.isIdentity || look.hsl.any { !it.isIdentity } || !look.curves.isIdentity
    ToolPanel(
        stringResource(R.string.adjust_title),
        onDone,
        modifier,
        actions = {
            ResetIconButton(actions::onReset, enabled = edited)
            onApplyAll?.let { ApplyToAllButton(it) }
        },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Adjustment.entries.forEach { adjustment ->
                val current = look.adjust[adjustment]
                PreCutChip(
                    label = stringResource(adjustment.labelRes()),
                    selected = adjustment == selected,
                    onClick = { selected = adjustment },
                    icon = adjustment.iconRes(),
                    value = current.takeIf { it != 0f }?.roundToInt()?.toString(),
                )
            }
            PreCutChip(stringResource(R.string.adjust_hsl), selected = false, onClick = actions::onHsl, dot = look.hsl.any { !it.isIdentity })
            PreCutChip(stringResource(R.string.adjust_curves), selected = false, onClick = actions::onCurves, dot = !look.curves.isIdentity)
        }
        val bipolar = selected.min < 0f
        // RD-T07 / RD-R-T07: range ends in muted Mono, a zero mark and a bipolar fill, a signed bubble.
        Row(Modifier.heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(selected.min.roundToInt().toString().replace('-', '−'), style = PreCutType.timecode, color = TextMuted, modifier = Modifier.width(40.dp))
            PreCutSlider(
                value = value,
                onValueChange = { value = it.roundToInt().toFloat() },
                onValueChangeFinished = { actions.onAdjust(selected, value) },
                valueRange = selected.min..selected.max,
                anchor = if (bipolar) 0f else null,
                bubble = { signed(it.roundToInt(), bipolar) },
                onReset = { actions.onAdjust(selected, 0f) },
                snapTo = if (bipolar) listOf(0f) else emptyList(),
                modifier = Modifier.weight(1f),
            )
            Text(selected.max.roundToInt().toString(), style = PreCutType.timecode, color = TextMuted, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
        }
    }
}

/** Icons on the first chips, as in the design. */
@DrawableRes
private fun Adjustment.iconRes(): Int? = when (this) {
    Adjustment.BRIGHTNESS -> R.drawable.ic_light_mode
    Adjustment.CONTRAST -> R.drawable.ic_contrast
    Adjustment.EXPOSURE -> R.drawable.ic_exposure
    Adjustment.SATURATION -> R.drawable.ic_palette
    Adjustment.SHARPEN -> R.drawable.ic_details
    else -> null
}

/** A slider value with a typographic sign: "+40", "−12", "0" (unsigned for one-sided settings). */
private fun signed(value: Int, bipolar: Boolean): String = when {
    value > 0 && bipolar -> "+$value"
    value < 0 -> "−${-value}"
    else -> value.toString()
}
