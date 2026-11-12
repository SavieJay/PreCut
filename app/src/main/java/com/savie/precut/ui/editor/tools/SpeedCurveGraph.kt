package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.model.speed.SpeedPreset
import com.savie.precut.ui.theme.PreCutType
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The speed curve graph (RD-T03, the design's `.graph`): source left to right, 0.1x–10x on a log scale. Drag a point to
 * move it (the end points only up and down), tap empty space to add one, long-press to remove one.
 * The edit is reported on release.
 */
@Composable
fun SpeedCurveGraph(curve: SpeedCurve, onChange: (SpeedCurve) -> Unit, modifier: Modifier = Modifier) {
    var editing by remember(curve) { mutableStateOf(curve) }
    var active by remember(curve) { mutableIntStateOf(-1) }
    val current by rememberUpdatedState(editing)
    val haptics = LocalHapticFeedback.current
    val description = stringResource(R.string.speed_curve_graph)
    Row(modifier.fillMaxWidth().height(GraphHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // The speed scale beside the graph (RD-T03): 10x top, 1x centre, 0.1x bottom.
        Column(
            Modifier.width(28.dp).fillMaxHeight().padding(vertical = 4.dp).clearAndSetSemantics {},
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            listOf("10x" to TextMuted, "1x" to TextPrimary, "0.1x" to TextMuted).forEach { (label, color) ->
                Text(label, style = ScaleStyle, color = color)
            }
        }
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphBox()
                .semantics { contentDescription = description }
                .pointerInput(curve) {
                    val area = { SpeedGraphArea(PointRadius.toPx(), PointRadius.toPx(), size.width - PointRadius.toPx(), size.height - PointRadius.toPx()) }
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
                                active = hit
                                var position = down.position
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    position += change.positionChange()
                                    change.consume()
                                    editing = current.withPoint(hit, graph.toPoint(position))
                                } while (change.pressed)
                                active = -1
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
            val graph = SpeedGraphArea(PointRadius.toPx(), PointRadius.toPx(), size.width - PointRadius.toPx(), size.height - PointRadius.toPx())
            val y = graph.yOf(1f)
            drawLine(
                TextMuted, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            drawSpeedCurve(editing, graph, active)
        }
    }
}

private val GraphHeight = 150.dp
private val PointRadius = 10.dp
private val ScaleStyle = PreCutType.timecode.copy(fontSize = 10.sp, lineHeight = 12.sp)
private val TouchRadius = 24.dp
