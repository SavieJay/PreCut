package com.savie.precut.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.onboarding.ClipScene
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0

private val ClipEdge = Color.White.copy(alpha = 0.14f)
private val RingTrack = Color.White.copy(alpha = 0.10f)

/** RD-H01's illustration: three 9:16 clips fanned out, the front one with a pink glow. */
@Composable
internal fun ClipFan(modifier: Modifier = Modifier) {
    Box(modifier.size(width = 180.dp, height = 200.dp)) {
        FanClip(ClipScene.HIKE, 96.dp, Modifier.offset(x = 4.dp, y = 18.dp).rotate(-12f))
        FanClip(ClipScene.FOOD, 96.dp, Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 18.dp).rotate(12f))
        FanClip(ClipScene.BEACH, 96.dp, Modifier.align(Alignment.TopCenter), glow = true)
    }
}

/**
 * RD-H07: a 280 dp ring around a fan of clips. Import progress isn't reported, so a pink arc
 * sweeps round the track (indeterminate).
 */
@Composable
internal fun ImportRing(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "import")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    Box(
        modifier
            .size(280.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            drawCircle(Accent.copy(alpha = 0.12f), radius = size.minDimension / 2)
            drawCircle(Ink0, radius = size.minDimension / 2 - stroke)
            val inset = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(RingTrack, 0f, 360f, false, topLeft = inset, size = arc, style = Stroke(stroke))
            drawArc(Accent, angle - 90f, 110f, false, topLeft = inset, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        FanClip(ClipScene.FOOD, 84.dp, Modifier.offset(x = (-38).dp).rotate(-14f))
        FanClip(ClipScene.MARKET, 84.dp, Modifier.offset(x = 38.dp).rotate(14f))
        FanClip(ClipScene.BEACH, 84.dp, Modifier, glow = true)
    }
}

@Composable
private fun FanClip(scene: ClipScene, width: Dp, modifier: Modifier, glow: Boolean = false) {
    val shape = RoundedCornerShape(if (width > 90.dp) 18.dp else 14.dp)
    Box(
        modifier
            .width(width)
            .aspectRatio(9f / 16f)
            .shadow(if (glow) 24.dp else 14.dp, shape, ambientColor = Color.Transparent, spotColor = if (glow) Accent else Color.Black)
            .background(scene.brush, shape)
            .border(1.dp, ClipEdge, shape),
    )
}
