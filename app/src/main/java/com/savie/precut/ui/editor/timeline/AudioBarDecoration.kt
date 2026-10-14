package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.BeatDot
import com.savie.precut.media.WaveformLoader
import com.savie.precut.model.AudioClip
import com.savie.precut.model.timelineAt

private val WaveColor = Color.White.copy(alpha = 0.42f)

/** Waveform (normalized to the loudest peak) and beat dots, drawn across an audio bar. */
@Composable
fun AudioBarDecoration(audio: AudioClip, peaks: FloatArray?, pxPerMs: Float, modifier: Modifier = Modifier) {
    val loudest = remember(peaks) { peaks?.maxOrNull()?.takeIf { it > 0f } ?: 1f }
    Canvas(modifier.fillMaxWidth()) {
        if (peaks != null) {
            val step = 3.dp.toPx()
            val stroke = 1.5.dp.toPx()
            var x = step / 2
            while (x < size.width) {
                val sourceMs = audio.trimStartMs + (x / pxPerMs * audio.speed).toLong()
                val amplitude = peaks.getOrElse((sourceMs / WaveformLoader.BUCKET_MS).toInt()) { 0f } / loudest
                val half = (amplitude * size.height * 0.4f).coerceAtLeast(stroke / 2)
                drawLine(WaveColor, Offset(x, size.height / 2 - half), Offset(x, size.height / 2 + half), strokeWidth = stroke)
                x += step
            }
        }
        val radius = 2.dp.toPx() // 4 dp beat dots, 1 dp above the bar's bottom
        audio.beatsMs.forEach { beat ->
            val x = (audio.timelineAt(beat) - audio.timelineStartMs) * pxPerMs
            if (x in 0f..size.width) drawCircle(BeatDot, radius, Offset(x, size.height - radius - 1.dp.toPx()))
        }
    }
}
