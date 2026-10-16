package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.savie.precut.model.OutputSize
import com.savie.precut.model.sticker.DRAW_REFERENCE_WIDTH
import com.savie.precut.model.sticker.DrawStroke
import com.savie.precut.model.sticker.Drawing
import kotlin.math.min

/**
 * S-02: the drawing over the frame. A finger draws with the current brush; each lifted stroke is
 * handed to [onStroke] in frame fractions. Erasing clears only the drawing, never the video.
 */
@Composable
fun DrawOverlay(drawing: Drawing, outputSize: OutputSize, onStroke: (List<Pair<Float, Float>>) -> Unit) {
    val currentOnStroke by rememberUpdatedState(onStroke)
    val currentDrawing by rememberUpdatedState(drawing)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = FrameRect.fitting(outputSize, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val currentFrame by rememberUpdatedState(frame)
        val live = remember { mutableStateListOf<Pair<Float, Float>>() }
        Canvas(
            Modifier
                .fillMaxSize()
                // Offscreen, so the eraser's Clear blend cuts the drawing and not the video under it.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        fun norm(p: Offset) = Pair(
                            ((p.x - currentFrame.left) / currentFrame.width).coerceIn(0f, 1f),
                            ((p.y - currentFrame.top) / currentFrame.height).coerceIn(0f, 1f),
                        )
                        val down = awaitFirstDown()
                        down.consume()
                        live.clear()
                        live += norm(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.positionChange() != Offset.Zero) {
                                live += norm(change.position)
                                change.consume()
                            }
                        }
                        currentOnStroke(live.toList())
                        live.clear()
                    }
                },
        ) {
            val short = min(frame.width, frame.height)
            currentDrawing.strokes.forEach { drawStroke(it, frame, short) }
            if (live.isNotEmpty()) {
                val d = currentDrawing
                drawStroke(DrawStroke(live.toList(), d.colorArgb, d.size / DRAW_REFERENCE_WIDTH, d.eraser), frame, short)
            }
        }
    }
}

private fun DrawScope.drawStroke(stroke: DrawStroke, frame: FrameRect, shortSide: Float) {
    val width = stroke.widthNorm * shortSide
    val color = if (stroke.eraser) Color.Transparent else Color(stroke.colorArgb)
    val blend = if (stroke.eraser) BlendMode.Clear else BlendMode.SrcOver
    val first = frame.toPx(stroke.points.first().first, stroke.points.first().second)
    if (stroke.points.size == 1) {
        drawCircle(color, radius = width / 2f, center = first, blendMode = blend)
        return
    }
    val path = Path().apply {
        moveTo(first.x, first.y)
        stroke.points.drop(1).forEach { (x, y) -> frame.toPx(x, y).let { lineTo(it.x, it.y) } }
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
}
