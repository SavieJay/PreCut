package com.savie.precut.ui.editor.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.model.speed.SpeedPoint
import com.savie.precut.ui.theme.Accent
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/** The speed curve's line and points (RD-T03): a 3 dp pink line, 20 dp points, the dragged one pink. */
internal fun DrawScope.drawSpeedCurve(curve: SpeedCurve, graph: SpeedGraphArea, active: Int) {
    val path = Path()
    for (i in 0..SAMPLES) {
        val x = i / SAMPLES.toFloat()
        val point = graph.toPx(SpeedPoint(x, curve.speedAt(x)))
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, Accent, style = Stroke(3.dp.toPx()))
    curve.points.forEachIndexed { i, p -> drawGraphPoint(graph.toPx(p), active = i == active) }
}

/** The plotting rectangle, in px: x 0..1 maps to [left]..[right], speed (log) to [bottom]..[top]. */
internal class SpeedGraphArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun yOf(speed: Float): Float = bottom - (log10(speed) + 1f) / 2f * (bottom - top)
    fun toPx(p: SpeedPoint) = Offset(left + p.x * (right - left), yOf(p.speed))
    fun toPoint(o: Offset): SpeedPoint {
        val x = ((o.x - left) / (right - left)).coerceIn(0f, 1f)
        val log = ((bottom - o.y) / (bottom - top) * 2f - 1f).coerceIn(-1f, 1f)
        return SpeedPoint(x, snapToOne(10f.pow(log)))
    }
}

/** [curve] with point [index] moved to [to]: ends keep their x; inner points stay between their neighbours. */
internal fun SpeedCurve.withPoint(index: Int, to: SpeedPoint): SpeedCurve {
    val x = when (index) {
        0 -> 0f
        points.lastIndex -> 1f
        else -> to.x.coerceIn(points[index - 1].x + MIN_GAP, points[index + 1].x - MIN_GAP)
    }
    return copy(points = points.toMutableList().also { it[index] = SpeedPoint(x, to.speed) })
}

/** Speeds within 8% of 1x snap to it, so a flat stretch is easy to make. */
private fun snapToOne(speed: Float): Float = if (abs(speed - 1f) < 0.08f) 1f else speed

private const val SAMPLES = 64
private const val MIN_GAP = 0.02f
