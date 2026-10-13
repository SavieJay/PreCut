package com.savie.precut.ui.editor.preview

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.savie.precut.model.OutputSize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where the letterboxed video frame sits inside the preview box (ContentScale.Fit), in px. */
data class FrameRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    /** Position of a 0..1 frame point (y down) in preview px. */
    fun toPx(xNorm: Float, yNorm: Float) = Offset(left + xNorm * width, top + yNorm * height)

    companion object {
        fun fitting(output: OutputSize, boxWidth: Float, boxHeight: Float): FrameRect {
            val scale = min(boxWidth / output.width, boxHeight / output.height)
            val width = output.width * scale
            val height = output.height * scale
            return FrameRect((boxWidth - width) / 2, (boxHeight - height) / 2, width, height)
        }
    }
}

/**
 * Whether [point] is on a box of [size] centred at [center] and rotated clockwise by
 * [rotationDegrees], with [slopPx] of extra margin.
 */
fun hitsText(point: Offset, center: Offset, size: Offset, rotationDegrees: Float, slopPx: Float): Boolean {
    val radians = Math.toRadians(-rotationDegrees.toDouble())
    val dx = point.x - center.x
    val dy = point.y - center.y
    val localX = dx * cos(radians) - dy * sin(radians)
    val localY = dx * sin(radians) + dy * cos(radians)
    return abs(localX) <= size.x / 2 + slopPx && abs(localY) <= size.y / 2 + slopPx
}

/** Measures with at most [maxWidthPx] and places the content centred on [center]; reports its size. */
fun Modifier.centredAt(center: Offset, maxWidthPx: Float, onSize: (Offset) -> Unit): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints(maxWidth = maxWidthPx.roundToInt().coerceAtLeast(1)))
    onSize(Offset(placeable.width.toFloat(), placeable.height.toFloat()))
    layout(placeable.width, placeable.height) {
        placeable.place((center.x - placeable.width / 2f).roundToInt(), (center.y - placeable.height / 2f).roundToInt())
    }
}

private val GuideColor = Color(0xFF38D9A9)

/** Centre guide lines across the frame for the snaps that are active. */
fun DrawScope.drawGuides(frame: FrameRect, snaps: TextTransform.Snaps) {
    val stroke = 1.dp.toPx()
    if (snaps.centreX) {
        val x = frame.left + frame.width / 2
        drawLine(GuideColor, Offset(x, frame.top), Offset(x, frame.top + frame.height), stroke)
    }
    if (snaps.centreY) {
        val y = frame.top + frame.height / 2
        drawLine(GuideColor, Offset(frame.left, y), Offset(frame.left + frame.width, y), stroke)
    }
}
