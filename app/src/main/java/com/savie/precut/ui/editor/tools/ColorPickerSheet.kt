package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.ColorDot
import com.savie.precut.ui.components.DonePill
import com.savie.precut.ui.components.formatHexColor
import com.savie.precut.ui.components.parseHexColor
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/**
 * X-03: saturation/brightness square, hue bar, a preview swatch with a hex field, and the
 * colours already used in the project. Done applies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(initialArgb: Int, recentColors: List<Int>, onPicked: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initialArgb, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(hue, saturation, value)
    var hex by remember(color) { mutableStateOf(formatHexColor(color.toArgb())) }

    fun setArgb(argb: Int) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
        hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Ink1,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.color_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                DonePill(onClick = { onPicked(color.toArgb()) })
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { at ->
                            saturation = (at.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (at.y / size.height).coerceIn(0f, 1f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        }
                    },
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                drawThumb(Offset(saturation * size.width, (1 - value) * size.height), color)
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(Unit) { detectTapGestures { hue = (it.x / size.width).coerceIn(0f, 1f) * 360f } }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ -> hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f }
                    },
            ) {
                val barHeight = 16.dp.toPx()
                val top = (size.height - barHeight) / 2
                drawRoundRect(
                    Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
                    Offset(0f, top),
                    Size(size.width, barHeight),
                    CornerRadius(barHeight / 2),
                )
                drawThumb(Offset(hue / 360f * size.width, size.height / 2), Color.hsv(hue, 1f, 1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(color, RoundedCornerShape(12.dp)))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        parseHexColor(text)?.let(::setArgb)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.color_hex)) },
                    textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Outline, cursorColor = Accent),
                    modifier = Modifier.weight(1f),
                )
            }
            if (recentColors.isNotEmpty()) {
                Text(stringResource(R.string.color_recent), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    recentColors.take(8).forEach { argb ->
                        ColorDot(Color(argb), selected = argb == color.toArgb(), description = formatHexColor(argb), onClick = { setArgb(argb) })
                    }
                }
            }
        }
    }
}

/** A 24 dp thumb: the colour inside a 3 dp white ring with a faint dark edge. */
private fun DrawScope.drawThumb(center: Offset, fill: Color) {
    val radius = 12.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.4f), radius + 1.dp.toPx(), center)
    drawCircle(Color.White, radius, center)
    drawCircle(fill, radius - 3.dp.toPx(), center)
    drawCircle(Color.White, radius, center, style = Stroke(0.5.dp.toPx()))
}
