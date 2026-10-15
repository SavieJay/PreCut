package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.speed.MAX_CURVE_SPEED
import com.savie.precut.model.speed.MIN_CURVE_SPEED
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.model.speed.SpeedPoint
import com.savie.precut.model.speed.SpeedPreset
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextSecondary
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * The speed curve graph (T-03): source left to right, 0.1x–10x on a log scale. Drag a point to
 * move it (the end points only up and down), tap empty space to add one, long-press to remove one.
 * The edit is reported on release.
 */
@Composable
fun SpeedCurveGraph(curve: SpeedCurve, onChange: (SpeedCurve) -> Unit, modifier: Modifier = Modifier) {
    var editing by remember(curve) { mutableStateOf(curve) }
    val current by rememberUpdatedState(editing)
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val labelStyle = PreCutType.labelChip.copy(color = TextSecondary)
    val description = stringResource(R.string.speed_curve_graph)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(GraphHeight)
            .semantics { contentDescription = description }
            .pointerInput(curve) {
                val area = { GraphArea(LabelWidth.toPx(), PointRadius.toPx(), size.width - PointRadius.toPx(), size.height - PointRadius.toPx()) }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val graph = area()
                    val hit = current.points.indices.minByOrNull { (graph.toPx(current.points[it]) - down.position).getDistance() }
                        ?.takeIf { (graph.toPx(current.points[it]) - down.position).getDistance() <= TouchRadius.toPx() }
                    var moved = false
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                            if (!change.pressed) return@withTimeoutOrNull
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                                return@withTimeoutOrNull
                            }
                        }
                    } == null
                    when {
                        held && hit != null && hit != 0 && hit != current.points.lastIndex -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onChange(current.copy(points = current.points.filterIndexed { i, _ -> i != hit }, preset = SpeedPreset.CUSTOM))
                        }
                        moved && hit != null -> {
                            var position = down.position
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                position += change.positionChange()
                                change.consume()
                                editing = current.withPoint(hit, graph.toPoint(position))
                            } while (change.pressed)
                            onChange(editing.copy(preset = SpeedPreset.CUSTOM))
                        }
                        !moved && !held && hit == null -> {
                            val point = graph.toPoint(down.position)
                            if (point.x > 0f && point.x < 1f) {
                                onChange(current.copy(points = (current.points + point).sortedBy { it.x }, preset = SpeedPreset.CUSTOM))
                            }
                        }
                    }
                }
            },
    ) {
        val graph = GraphArea(LabelWidth.toPx(), PointRadius.toPx(), size.width - PointRadius.toPx(), size.height - PointRadius.toPx())
        listOf(MAX_CURVE_SPEED to "10x", 1f to "1x", MIN_CURVE_SPEED to "0.1x").forEach { (speed, label) ->
            val y = graph.yOf(speed)
            drawLine(
                Ink3, Offset(graph.left, y), Offset(graph.right, y), 1.dp.toPx(),
                pathEffect = if (speed == 1f) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            val text = measurer.measure(label, labelStyle)
            drawText(text, topLeft = Offset(0f, (y - text.size.height / 2f).coerceIn(0f, size.height - text.size.height)))
        }
        drawCurve(editing, graph)
    }
}

private fun DrawScope.drawCurve(curve: SpeedCurve, graph: GraphArea) {
    val path = Path()
    for (i in 0..SAMPLES) {
        val x = i / SAMPLES.toFloat()
        val point = graph.toPx(SpeedPoint(x, curve.speedAt(x)))
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, Accent, style = Stroke(2.dp.toPx()))
    curve.points.forEach { p ->
        val center = graph.toPx(p)
        drawCircle(Color.White, PointRadius.toPx(), center)
        drawCircle(Accent, PointRadius.toPx(), center, style = Stroke(2.dp.toPx()))
    }
}

/** The plotting rectangle, in px: x 0..1 maps to [left]..[right], speed (log) to [bottom]..[top]. */
private class GraphArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun yOf(speed: Float): Float = bottom - (log10(speed) + 1f) / 2f * (bottom - top)
    fun toPx(p: SpeedPoint) = Offset(left + p.x * (right - left), yOf(p.speed))
    fun toPoint(o: Offset): SpeedPoint {
        val x = ((o.x - left) / (right - left)).coerceIn(0f, 1f)
        val log = ((bottom - o.y) / (bottom - top) * 2f - 1f).coerceIn(-1f, 1f)
        return SpeedPoint(x, snapToOne(10f.pow(log)))
    }
}

/** [curve] with point [index] moved to [to]: ends keep their x; inner points stay between their neighbours. */
private fun SpeedCurve.withPoint(index: Int, to: SpeedPoint): SpeedCurve {
    val x = when (index) {
        0 -> 0f
        points.lastIndex -> 1f
        else -> to.x.coerceIn(points[index - 1].x + MIN_GAP, points[index + 1].x - MIN_GAP)
    }
    return copy(points = points.toMutableList().also { it[index] = SpeedPoint(x, to.speed) })
}

/** Speeds within 8% of 1x snap to it, so a flat stretch is easy to make. */
private fun snapToOne(speed: Float): Float = if (abs(speed - 1f) < 0.08f) 1f else speed

private val GraphHeight = 160.dp
private val LabelWidth = 36.dp
private val PointRadius = 7.dp
private val TouchRadius = 24.dp
private const val SAMPLES = 64
private const val MIN_GAP = 0.02f
