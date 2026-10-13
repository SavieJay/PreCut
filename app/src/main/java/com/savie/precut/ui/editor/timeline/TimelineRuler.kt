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

/** Time ruler: a tick per interval (labelled on whole seconds) and a minor tick halfway between. */
@Composable
fun TimelineRuler(totalMs: Long, pxPerMs: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
    Canvas(modifier) {
        val interval = TimelineGeometry.tickIntervalMs(pxPerMs, minSpacingPx = 56.dp.toPx())
        val majorHeight = 6.dp.toPx()
        val minorHeight = 3.dp.toPx()
        var ms = 0L
        while (ms <= totalMs) {
            val x = TimelineGeometry.msToPx(ms, pxPerMs)
            drawLine(RulerTick, Offset(x, size.height - majorHeight), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            if (ms % 1000 == 0L) { // labels are whole seconds; sub-second ticks stay unlabelled
                val label = measurer.measure(formatDurationMs(ms), labelStyle)
                drawText(label, topLeft = Offset(x - label.size.width / 2f, 0f))
            }
            val minorX = x + TimelineGeometry.msToPx(interval / 2, pxPerMs)
            if (ms + interval / 2 <= totalMs) {
                drawLine(RulerTick, Offset(minorX, size.height - minorHeight), Offset(minorX, size.height), strokeWidth = 1.dp.toPx())
            }
            ms += interval
        }
    }
}
