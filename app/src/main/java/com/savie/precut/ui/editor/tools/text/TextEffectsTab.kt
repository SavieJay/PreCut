package com.savie.precut.ui.editor.tools.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.MAX_TEXT_CURVE
import com.savie.precut.model.text.TextGlow
import com.savie.precut.model.text.TextShadow
import com.savie.precut.model.text.TextStroke
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.components.SwitchRow
import com.savie.precut.ui.editor.tools.ColorSwatchRow
import com.savie.precut.ui.editor.tools.backgroundFor
import kotlin.math.roundToInt

/** Effects (X-04c): Stroke, Shadow, Glow, Background and Curve, each switched on with its own controls. */
@Composable
fun TextEffectsTab(overlay: TextOverlay, recentColors: List<Int>, onChange: (TextOverlay) -> Unit) {
    var effect by rememberSaveable { mutableIntStateOf(0) }
    PillTabs(
        listOf(R.string.text_stroke, R.string.text_shadow, R.string.text_glow, R.string.text_background, R.string.text_curve).map { PillTab(stringResource(it)) },
        selected = effect,
        onSelect = { effect = it },
    )
    when (effect) {
        0 -> {
            val stroke = overlay.stroke
            SwitchRow(stringResource(R.string.text_stroke), stroke != null, { on -> onChange(overlay.copy(stroke = if (on) TextStroke() else null)) })
            if (stroke != null) {
                ColorSwatchRow(stroke.colorArgb, recentColors) { onChange(overlay.copy(stroke = stroke.copy(colorArgb = it))) }
                Amount(R.string.text_width, stroke.width, 0f..12f) { onChange(overlay.copy(stroke = stroke.copy(width = it))) }
            }
        }
        1 -> {
            val shadow = overlay.shadow
            SwitchRow(stringResource(R.string.text_shadow), shadow != null, { on -> onChange(overlay.copy(shadow = if (on) TextShadow() else null)) })
            if (shadow != null) {
                ColorSwatchRow(shadow.colorArgb, recentColors) { onChange(overlay.copy(shadow = shadow.copy(colorArgb = it))) }
                Amount(R.string.text_blur, shadow.blur, 0f..20f) { onChange(overlay.copy(shadow = shadow.copy(blur = it))) }
                Amount(R.string.text_distance, shadow.distance, 0f..20f) { onChange(overlay.copy(shadow = shadow.copy(distance = it))) }
                Amount(R.string.text_angle, shadow.angleDegrees, 0f..360f, suffix = "°") { onChange(overlay.copy(shadow = shadow.copy(angleDegrees = it))) }
            }
        }
        2 -> {
            val glow = overlay.glow
            SwitchRow(stringResource(R.string.text_glow), glow != null, { on -> onChange(overlay.copy(glow = if (on) TextGlow() else null)) })
            if (glow != null) {
                ColorSwatchRow(glow.colorArgb, recentColors) { onChange(overlay.copy(glow = glow.copy(colorArgb = it))) }
                Amount(R.string.text_strength, glow.strength * 100f, 0f..100f) { onChange(overlay.copy(glow = glow.copy(strength = it / 100f))) }
                Amount(R.string.text_spread, glow.spread, 0f..30f) { onChange(overlay.copy(glow = glow.copy(spread = it))) }
            }
        }
        3 -> {
            val colour = overlay.backgroundArgb
            SwitchRow(
                stringResource(R.string.text_background), colour != null,
                { on -> onChange(overlay.copy(backgroundArgb = if (on) backgroundFor(overlay.colorArgb) else null)) },
            )
            if (colour != null) {
                ColorSwatchRow(colour, recentColors) { onChange(overlay.copy(backgroundArgb = it)) }
                Amount(R.string.text_opacity, overlay.backgroundOpacity * 100f, 0f..100f, suffix = "%") { onChange(overlay.copy(backgroundOpacity = it / 100f)) }
                Amount(R.string.mask_corners, overlay.backgroundCorners ?: (overlay.textSizeSp * 0.2f), 0f..30f) { onChange(overlay.copy(backgroundCorners = it)) }
                Amount(R.string.text_padding, overlay.backgroundPadding ?: (overlay.textSizeSp * 0.25f), 0f..30f) { onChange(overlay.copy(backgroundPadding = it)) }
            }
        }
        else -> Amount(R.string.text_curve_amount, overlay.curve, -MAX_TEXT_CURVE..MAX_TEXT_CURVE, anchor = 0f, signed = true) { onChange(overlay.copy(curve = it)) }
    }
}

/** A labelled slider that applies on release; whole numbers. */
@Composable
private fun Amount(
    label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    anchor: Float? = null,
    signed: Boolean = false,
    onChanged: (Float) -> Unit,
) {
    var shown by remember(value) { mutableFloatStateOf(value) }
    val number = shown.roundToInt()
    SliderRow(value = (if (signed && number > 0) "+$number" else "$number") + suffix, label = stringResource(label)) { modifier ->
        PreCutSlider(
            value = shown,
            onValueChange = { shown = it.roundToInt().toFloat() },
            onValueChangeFinished = { onChanged(shown) },
            valueRange = range,
            anchor = anchor,
            modifier = modifier,
        )
    }
}
