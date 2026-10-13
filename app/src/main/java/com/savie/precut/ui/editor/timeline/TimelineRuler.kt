package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.RulerTick
import com.savie.precut.ui.theme.TextSecondary

/** Time ruler (see [TimelineGeometry.rulerTicks]): labelled seconds, and frames ("10f") when zoomed in. */
@Composable
fun TimelineRuler(totalMs: Long, pxPerMs: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
    Canvas(modifier) {
        val majorHeight = 6.dp.toPx()
        val minorHeight = 3.dp.toPx()
        TimelineGeometry.rulerTicks(pxPerMs, minSpacingPx = 56.dp.toPx(), totalMs).forEach { tick ->
            val x = TimelineGeometry.msToPx(tick.ms, pxPerMs)
            val height = if (tick.label == null) minorHeight else majorHeight
            drawLine(RulerTick, Offset(x, size.height - height), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            val text = when (val label = tick.label) {
                is TimelineGeometry.TickLabel.Time -> formatDurationMs(label.ms)
                is TimelineGeometry.TickLabel.Frame -> "${label.frame}f"
                null -> return@forEach
            }
            val measured = measurer.measure(text, labelStyle)
            drawText(measured, topLeft = Offset(x - measured.size.width / 2f, 0f))
        }
    }
}
