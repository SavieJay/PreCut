package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.frame.FrameSnaps
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.SnapGuide
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.tabular
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** [v] rotated clockwise (screen space) by [degrees]. */
/** [at] moved inside a [width] × [height] area, [margin] from its edges, so a handle stays on the preview. */
internal fun keepInside(at: Offset, width: Float, height: Float, margin: Float): Offset =
    Offset(at.x.coerceIn(margin, (width - margin).coerceAtLeast(margin)), at.y.coerceIn(margin, (height - margin).coerceAtLeast(margin)))

internal fun rotate(v: Offset, degrees: Float): Offset {
    val r = Math.toRadians(degrees.toDouble())
    return Offset((v.x * cos(r) - v.y * sin(r)).toFloat(), (v.x * sin(r) + v.y * cos(r)).toFloat())
}

/** A 24 dp white handle with an Ink0 icon, centred on [at]. */
@Composable
internal fun FrameHandle(icon: Int, description: String, at: Offset, input: () -> Modifier) {
    Box(
        Modifier
            .offset { IntOffset((at.x - 12.dp.toPx()).roundToInt(), (at.y - 12.dp.toPx()).roundToInt()) }
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White)
            .then(input())
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Ink0, modifier = Modifier.size(16.dp))
    }
}

/** T-15's outline: a 2 dp white border just outside the clip, 20 dp corner dots, and snap guides. */
internal fun DrawScope.drawClipFrame(box: ClipBox, snaps: FrameSnaps?, frame: FrameRect) {
    val stroke = 2.dp.toPx()
    val topLeft = Offset(box.center.x - box.size.x / 2 - stroke / 2, box.center.y - box.size.y / 2 - stroke / 2)
    rotate(box.rotationDegrees, pivot = box.center) {
        drawRect(Color.White, topLeft, Size(box.size.x + stroke, box.size.y + stroke), style = Stroke(stroke))
        val dot = 10.dp.toPx()
        for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
            drawCircle(Color.White, dot, Offset(box.center.x + sx * box.size.x / 2, box.center.y + sy * box.size.y / 2))
        }
    }
    if (snaps == null) return
    val hair = 1.dp.toPx()
    if (snaps.centreX) {
        val x = frame.left + frame.width / 2
        drawLine(SnapGuide, Offset(x, frame.top), Offset(x, frame.top + frame.height), hair)
    }
    if (snaps.centreY) {
        val y = frame.top + frame.height / 2
        drawLine(SnapGuide, Offset(frame.left, y), Offset(frame.left + frame.width, y), hair)
    }
}

/** The rotation readout while turning (T-15): a dark pill centred 10 dp below the frame's top. */
@Composable
internal fun AngleChip(degrees: Float, frame: FrameRect) {
    Row(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((frame.left + frame.width / 2 - placeable.width / 2f).roundToInt(), (frame.top + 10.dp.toPx()).roundToInt())
                }
            }
            .height(28.dp)
            .background(Color(0xD90E0E10), RoundedCornerShape(14.dp))
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(R.drawable.ic_rotate_right), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
        Text("${degrees.roundToInt()}°", style = PreCutType.labelChip.copy(fontWeight = FontWeight.SemiBold).tabular(), color = TextPrimary)
    }
}
