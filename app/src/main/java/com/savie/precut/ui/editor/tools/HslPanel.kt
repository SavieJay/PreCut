package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.savie.precut.R
import com.savie.precut.model.look.HslBand
import com.savie.precut.model.look.HslColor
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.ColorDot
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** T-08: pick one of 8 colours, then shift its hue, saturation and lightness on gradient sliders. */
@Composable
fun HslPanel(
    bands: List<HslBand>,
    onBand: (HslColor, HslBand) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var color by remember { mutableStateOf(HslColor.RED) }
    val band = bands.getOrNull(color.ordinal) ?: HslBand()
    val swatch = Color.hsv(color.hueDegrees, 0.72f, 0.92f)
    ToolPanel(
        stringResource(R.string.hsl_title),
        onDone,
        modifier,
        onBack = onBack,
        actions = {
            AccentTextButton(
                stringResource(R.string.adjust_reset),
                { HslColor.entries.forEach { onBand(it, HslBand()) } },
                enabled = bands.any { !it.isIdentity },
                color = TextSecondary,
            )
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HslColor.entries.forEach { c ->
                ColorDot(Color.hsv(c.hueDegrees, 0.72f, 0.92f), selected = c == color, description = stringResource(c.labelRes()), onClick = { color = c })
            }
        }
        Text(stringResource(color.labelRes()), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
        BandSlider(stringResource(R.string.hsl_hue), band.hue, color, Brush.horizontalGradient(listOf(-30f, 0f, 30f).map { Color.hsv((color.hueDegrees + it + 360f) % 360f, 0.72f, 0.92f) })) {
            onBand(color, band.copy(hue = it))
        }
        BandSlider(stringResource(R.string.hsl_saturation), band.saturation, color, Brush.horizontalGradient(listOf(Color(0xFF8A817A), swatch))) {
            onBand(color, band.copy(saturation = it))
        }
        BandSlider(stringResource(R.string.hsl_lightness), band.lightness, color, Brush.horizontalGradient(listOf(Color.Black, swatch, Color.White))) {
            onBand(color, band.copy(lightness = it))
        }
    }
}

@Composable
private fun BandSlider(label: String, applied: Float, color: HslColor, brush: Brush, onChanged: (Float) -> Unit) {
    var value by remember(color, applied) { mutableFloatStateOf(applied) }
    SliderRow(value = value.roundToInt().toString().replace('-', '−'), label = label) { modifier ->
        PreCutSlider(
            value = value,
            onValueChange = { value = it.roundToInt().toFloat() },
            onValueChangeFinished = { onChanged(value) },
            valueRange = -100f..100f,
            anchor = 0f,
            trackBrush = brush,
            snapTo = listOf(0f),
            onReset = { onChanged(0f) },
            modifier = modifier,
        )
    }
}
