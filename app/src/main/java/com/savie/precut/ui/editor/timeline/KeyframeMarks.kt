package com.savie.precut.ui.editor.timeline

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Ink0

/**
 * Keyframe diamonds (P-05, RD `.kf`) across the middle of a selected bar, [offsetsMs] from its
 * start: 12 dp white diamonds with a 2 dp Base 0 edge.
 */
@Composable
fun KeyframeMarks(offsetsMs: List<Long>, pxPerMs: Float, modifier: Modifier = Modifier) {
    if (offsetsMs.isEmpty()) return
    Canvas(modifier.fillMaxSize()) {
        val half = 8.dp.toPx() // a 12 dp square turned 45° is about 17 dp across
        val y = size.height / 2
        offsetsMs.forEach { offset ->
            val x = TimelineGeometry.msToPx(offset, pxPerMs)
            val diamond = Path().apply {
                moveTo(x, y - half)
                lineTo(x + half, y)
                lineTo(x, y + half)
                lineTo(x - half, y)
                close()
            }
            drawPath(diamond, Color.White)
            drawPath(diamond, Ink0, style = Stroke(2.dp.toPx()))
        }
    }
}
