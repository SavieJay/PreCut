package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// The picker's hue bar and thumbs (X-03).

/** The 14 dp rainbow bar; drag or tap to pick [hue] (0–360). */
@Composable
internal fun HueBar(hue: Float, onHue: (Float) -> Unit) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) { detectTapGestures { onHue((it.x / size.width).coerceIn(0f, 1f) * 360f) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onHue((change.position.x / size.width).coerceIn(0f, 1f) * 360f) }
            },
    ) {
        val barHeight = 14.dp.toPx()
        val top = (size.height - barHeight) / 2
        drawRoundRect(
            Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
            Offset(0f, top),
            Size(size.width, barHeight),
            CornerRadius(barHeight / 2),
        )
        drawThumb(Offset(hue / 360f * size.width, size.height / 2), Color.hsv(hue, 1f, 1f))
    }
}

/** A 24 dp thumb: the colour inside a 3 dp white ring with a faint dark edge. */
internal fun DrawScope.drawThumb(center: Offset, fill: Color) {
    val radius = 12.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.4f), radius + 1.dp.toPx(), center)
    drawCircle(Color.White, radius, center)
    drawCircle(fill, radius - 3.dp.toPx(), center)
    drawCircle(Color.White, radius, center, style = Stroke(0.5.dp.toPx()))
}
