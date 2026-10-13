package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.look.HslBand
import com.savie.precut.model.look.HslColor

/** CapCut's HSL: pick one of 8 colours, then shift its hue, saturation and lightness. */
@Composable
fun HslPanel(bands: List<HslBand>, onBand: (HslColor, HslBand) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var color by remember { mutableStateOf(HslColor.RED) }
    val band = bands.getOrNull(color.ordinal) ?: HslBand()
    ToolPanel(stringResource(R.string.hsl_title) + " · " + stringResource(color.labelRes()), onDone, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HslColor.entries.forEach { c ->
                ColorSwatch(Color.hsv(c.hueDegrees, 0.85f, 0.95f), selected = c == color) { color = c }
            }
        }
        BandSlider(stringResource(R.string.hsl_hue), band.hue, color) { onBand(color, band.copy(hue = it)) }
        BandSlider(stringResource(R.string.hsl_saturation), band.saturation, color) { onBand(color, band.copy(saturation = it)) }
        BandSlider(stringResource(R.string.hsl_lightness), band.lightness, color) { onBand(color, band.copy(lightness = it)) }
    }
}

@Composable
private fun BandSlider(label: String, applied: Float, color: HslColor, onChanged: (Float) -> Unit) {
    var value by remember(color, applied) { mutableFloatStateOf(applied) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp))
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChanged(value) },
            valueRange = -100f..100f,
            modifier = Modifier.weight(1f),
        )
        Text("${value.toInt()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
    }
}
