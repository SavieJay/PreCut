package com.savie.precut.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AiAccent

private val ProgressTrack = Color.White.copy(alpha = 0.12f)

/** An 8 dp progress bar (RD-CMP `.prog`): pink, or a lime gradient for AI work. */
@Composable
fun PreCutProgressBar(progress: Float, modifier: Modifier = Modifier, ai: Boolean = false, height: Dp = 8.dp) {
    val value = progress.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(height).semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(ProgressTrack, cornerRadius = radius)
        if (value > 0f) {
            val width = size.width * value
            val brush = if (ai) Brush.horizontalGradient(listOf(AiAccent.copy(alpha = 0.5f), AiAccent), endX = width) else Brush.linearGradient(listOf(Accent, Accent))
            drawRoundRect(brush, size = Size(width, size.height), cornerRadius = radius)
        }
    }
}

/** A progress ring (RD-CMP `.ring`): [progress] 0..1 in pink (lime for AI), 6 dp stroke. */
@Composable
fun PreCutProgressRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 64.dp, stroke: Dp = 6.dp, ai: Boolean = false) {
    val value = progress.coerceIn(0f, 1f)
    Canvas(modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }) {
        val width = stroke.toPx()
        val inset = width / 2
        val arc = Size(this.size.width - width, this.size.height - width)
        drawArc(ProgressTrack, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(width))
        drawArc(if (ai) AiAccent else Accent, -90f, 360f * value, false, Offset(inset, inset), arc, style = Stroke(width, cap = StrokeCap.Round))
    }
}

/** A 36 dp spinner (RD-CMP `.spin`): a pink arc turning once a second. */
@Composable
fun PreCutSpinner(modifier: Modifier = Modifier, size: Dp = 36.dp, ai: Boolean = false) {
    val turn by rememberInfiniteTransition(label = "spinner").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1_000, easing = LinearEasing)), label = "turn",
    )
    Canvas(modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val width = 4.dp.toPx()
        val inset = width / 2
        val arc = Size(this.size.width - width, this.size.height - width)
        drawArc(ProgressTrack, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(width))
        drawArc(if (ai) AiAccent else Accent, turn - 90f, 252f, false, Offset(inset, inset), arc, style = Stroke(width, cap = StrokeCap.Round))
    }
}

/**
 * The loading shimmer (RD-CMP `.shim`) drawn over this element: a light band sweeping left to
 * right; [ai] uses the lime sweep of AI generating states (1.6 s).
 */
fun Modifier.shimmer(ai: Boolean = false): Modifier = composed {
    val sweep by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(if (ai) PreCutMotion.SHIMMER_MS else 1_200, easing = LinearEasing), RepeatMode.Restart), label = "sweep",
    )
    val band = if (ai) AiAccent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
    drawWithContent {
        drawContent()
        val x = size.width * sweep
        drawRect(Brush.linearGradient(listOf(Color.Transparent, band, Color.Transparent), start = Offset(x - size.width * 0.4f, 0f), end = Offset(x, size.height * 0.2f)))
    }
}
