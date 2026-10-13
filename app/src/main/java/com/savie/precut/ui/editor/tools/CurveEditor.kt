package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.savie.precut.model.look.ColorMath
import com.savie.precut.model.look.CurveChannel
import com.savie.precut.model.look.CurvePoint
import kotlin.math.hypot

/**
 * A tone-curve editor: drag points (end points move only vertically), tap the curve to add a
 * point, long-press a point to remove it. Drawn with the same spline the shader uses; the new
 * points are reported when a gesture ends.
 */
@Composable
fun CurveEditor(points: List<CurvePoint>, channel: CurveChannel, onPoints: (List<CurvePoint>) -> Unit, modifier: Modifier = Modifier) {
    var live by remember(points) { mutableStateOf(points.sortedBy { it.x }) }
    var dragging by remember { mutableIntStateOf(-1) }
    val currentOnPoints by rememberUpdatedState(onPoints)
    val stroke = when (channel) {
        CurveChannel.RGB -> Color.White
        CurveChannel.RED -> Color(0xFFFF6B6B)
        CurveChannel.GREEN -> Color(0xFF51CF66)
        CurveChannel.BLUE -> Color(0xFF4DABF7)
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(channel, points) {
                val slop = 28.dp.toPx()
                detectTapGestures(
                    onTap = { at ->
                        val x = (at.x / size.width).coerceIn(0f, 1f)
                        if (nearest(live, at, size, slop) == null) {
                            val y = ColorMath.sample(ColorMath.curveTable(live), x)
                            currentOnPoints((live + CurvePoint(x, y)).sortedBy { it.x })
                        }
                    },
                    onLongPress = { at ->
                        val index = nearest(live, at, size, slop) ?: return@detectTapGestures
                        if (index != 0 && index != live.lastIndex) currentOnPoints(live.filterIndexed { i, _ -> i != index })
                    },
                )
            }
            .pointerInput(channel, points) {
                detectDragGestures(
                    onDragStart = { at -> dragging = nearest(live, at, size, 32.dp.toPx()) ?: -1 },
                    onDragEnd = {
                        if (dragging >= 0) currentOnPoints(live)
                        dragging = -1
                    },
                    onDragCancel = { dragging = -1 },
                ) { change, _ ->
                    val i = dragging
                    if (i < 0) return@detectDragGestures
                    change.consume()
                    val minX = if (i == 0) 0f else live[i - 1].x + 0.01f
                    val maxX = if (i == live.lastIndex) 1f else live[i + 1].x - 0.01f
                    val x = if (i == 0 || i == live.lastIndex) live[i].x else (change.position.x / size.width).coerceIn(minX, maxX)
                    val y = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    live = live.toMutableList().also { it[i] = CurvePoint(x, y) }
                }
            },
    ) {
        val grid = Color.White.copy(alpha = 0.12f)
        for (k in 1..3) {
            val f = k / 4f
            drawLine(grid, Offset(size.width * f, 0f), Offset(size.width * f, size.height))
            drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f))
        }
        drawLine(grid, Offset(0f, size.height), Offset(size.width, 0f))
        val table = ColorMath.curveTable(live, 64)
        val path = Path()
        table.forEachIndexed { i, y ->
            val point = Offset(size.width * i / (table.size - 1f), size.height * (1f - y))
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, stroke, style = Stroke(2.dp.toPx()))
        live.forEachIndexed { i, p ->
            val center = Offset(p.x * size.width, (1f - p.y) * size.height)
            drawCircle(if (i == dragging) stroke else Color.Black, 6.dp.toPx(), center)
            drawCircle(stroke, 6.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
    }
}

/** Index of the point nearest [at] within [slop] px, if any. */
private fun nearest(points: List<CurvePoint>, at: Offset, size: IntSize, slop: Float): Int? =
    points.indices
        .map { it to hypot(points[it].x * size.width - at.x, (1f - points[it].y) * size.height - at.y) }
        .filter { it.second <= slop }
        .minByOrNull { it.second }?.first
