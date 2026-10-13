package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.formatHexColor
import com.savie.precut.ui.components.parseHexColor

/** Full colour picker: saturation/brightness square, hue bar, hex field and the project's recent colours. */
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(Unit) {
                        fun pick(at: Offset) {
                            saturation = (at.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (at.y / size.height).coerceIn(0f, 1f)
                        }
                        detectTapGestures { pick(it) }
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
                drawCircle(Color.White, 10.dp.toPx(), Offset(saturation * size.width, (1 - value) * size.height), style = Stroke(2.dp.toPx()))
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
                drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                drawCircle(Color.White, size.height / 2, Offset(hue / 360f * size.width, size.height / 2), style = Stroke(2.dp.toPx()))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorSwatch(color, selected = true) {}
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        parseHexColor(text)?.let(::setArgb)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.color_hex)) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onPicked(color.toArgb()) }) { Text(stringResource(R.string.action_done)) }
            }
            if (recentColors.isNotEmpty()) {
                Text(stringResource(R.string.color_recent))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentColors.take(8).forEach { argb -> ColorSwatch(Color(argb), selected = false) { setArgb(argb) } }
                }
            }
        }
    }
}
