package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.tabular
import com.savie.precut.ui.theme.Playhead as PlayheadColor

/**
 * Fixed centre line marking the current time (RD-E03 redline 7): 2 dp white with a soft white glow
 * from under the ruler down, and a white 20 dp time bubble over the ruler. [positionMs] is read
 * while drawing, so only this canvas redraws as time moves; null draws the line alone.
 */
@Composable
fun Playhead(modifier: Modifier = Modifier, positionMs: (() -> Long)? = null) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.tabular().copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = Ink0)
    Canvas(modifier.width(88.dp).fillMaxHeight()) {
        val line = 2.dp.toPx()
        val cx = size.width / 2
        val top = (TopPadding + RulerHeight).toPx()
        val height = (size.height - top).coerceAtLeast(0f)
        // The glow: two wider, fainter bands under the line.
        drawRect(PlayheadColor.copy(alpha = 0.10f), Offset(cx - 5.dp.toPx(), top), Size(10.dp.toPx(), height))
        drawRect(PlayheadColor.copy(alpha = 0.18f), Offset(cx - 2.5.dp.toPx(), top), Size(5.dp.toPx(), height))
        drawRoundRect(PlayheadColor, Offset(cx - line / 2, top), Size(line, height), CornerRadius(line / 2))
        val time = positionMs ?: return@Canvas
        val text = measurer.measure(formatPreciseMs(time()), style)
        val bubbleH = 20.dp.toPx()
        val bubbleW = (text.size.width + 16.dp.toPx()).coerceAtMost(size.width)
        drawRoundRect(PlayheadColor, Offset(cx - bubbleW / 2, 0f), Size(bubbleW, bubbleH), CornerRadius(bubbleH / 2))
        drawText(text, topLeft = Offset(cx - text.size.width / 2f, (bubbleH - text.size.height) / 2f))
    }
}
