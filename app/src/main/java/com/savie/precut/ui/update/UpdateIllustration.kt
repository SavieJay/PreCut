package com.savie.precut.ui.update

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.TextSecondary

/** C1's picture: a phone with an arrow rising out of it, on a soft accent disc. Decorative. */
@Composable
internal fun UpdateIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.size(132.dp)) {
        val unit = size.minDimension / 132f
        drawCircle(AccentContainer, radius = size.minDimension / 2f)
        // The phone: a rounded body with a speaker slot, standing in the lower part of the disc.
        val phone = Size(52f * unit, 86f * unit)
        val phoneTop = Offset((size.width - phone.width) / 2f, 34f * unit)
        drawRoundRect(Ink2, phoneTop, phone, CornerRadius(10f * unit))
        drawRoundRect(TextSecondary, phoneTop, phone, CornerRadius(10f * unit), style = Stroke(3f * unit))
        drawLine(TextSecondary, Offset(center.x - 7f * unit, phoneTop.y + 8f * unit), Offset(center.x + 7f * unit, phoneTop.y + 8f * unit), 3f * unit, StrokeCap.Round)
        // The arrow, rising from the screen past the phone's top edge.
        val stroke = 6f * unit
        val tip = Offset(center.x, 18f * unit)
        drawLine(Accent, Offset(center.x, phoneTop.y + 62f * unit), tip, stroke, StrokeCap.Round)
        val head = Path().apply {
            moveTo(center.x - 15f * unit, tip.y + 15f * unit)
            lineTo(tip.x, tip.y)
            lineTo(center.x + 15f * unit, tip.y + 15f * unit)
        }
        drawPath(head, Accent, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
