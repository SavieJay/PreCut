package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Playhead as PlayheadColor

/** Fixed centre line marking the current time: 2 dp white with a dark halo, a 10 × 12 knob on top. */
@Composable
fun Playhead(modifier: Modifier = Modifier) {
    Canvas(modifier.width(12.dp).fillMaxHeight()) {
        val line = 2.dp.toPx()
        val halo = 1.dp.toPx()
        val cx = size.width / 2
        drawRect(Halo, Offset(cx - line / 2 - halo, 0f), Size(line + 2 * halo, size.height))
        drawRoundRect(PlayheadColor, Offset(cx - line / 2, 0f), Size(line, size.height), CornerRadius(line / 2))
        val knobW = 10.dp.toPx()
        val knobH = 12.dp.toPx()
        drawRoundRect(PlayheadColor, Offset(cx - knobW / 2, 0f), Size(knobW, knobH), CornerRadius(3.dp.toPx()))
    }
}

private val Halo = Color(0x590E0E10)
