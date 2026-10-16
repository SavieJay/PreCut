package com.savie.precut.ui.editor.timeline

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/** Keyframe diamonds (P-05) along the bottom of a selected bar, [offsetsMs] from its start. */
@Composable
fun KeyframeMarks(offsetsMs: List<Long>, pxPerMs: Float, modifier: Modifier = Modifier) {
    if (offsetsMs.isEmpty()) return
    Canvas(modifier.fillMaxSize()) {
        val half = 5.dp.toPx()
        val y = size.height - half - 3.dp.toPx()
        offsetsMs.forEach { offset ->
            val x = TimelineGeometry.msToPx(offset, pxPerMs)
            val diamond = Path().apply {
                moveTo(x, y - half)
                lineTo(x + half, y)
                lineTo(x, y + half)
                lineTo(x - half, y)
                close()
            }
            drawPath(diamond, Color.Black.copy(alpha = 0.5f), style = Stroke(3.dp.toPx()))
            drawPath(diamond, Color.White)
            drawCircle(Color.Black.copy(alpha = 0.35f), 1.5.dp.toPx(), Offset(x, y))
        }
    }
}
