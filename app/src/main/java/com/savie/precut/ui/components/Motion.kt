package com.savie.precut.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The 2026 motion spec (canvas note "Motion spec 2026"). Compose scales every animation by the
 * system's animator duration scale, so with animations removed in Android settings these finish
 * at once.
 */
object PreCutMotion {
    /** Panels and sheets entering: 260 ms, emphasized decelerate. */
    val enter = tween<Float>(260, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))

    /** Panels and sheets leaving: 200 ms, emphasized accelerate. */
    val exit = tween<Float>(200, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))

    /** The playhead jumping to an edge, beat or keyframe (~120 ms). */
    val snap = spring<Float>(dampingRatio = 0.75f, stiffness = 800f)

    /** AI generating states: one shimmer sweep. */
    const val SHIMMER_MS = 1_600
}

/** Every button, chip, tile and tool scales to 0.96 while pressed and springs back. */
fun Modifier.pressScale(interaction: InteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (pressed) tween(80) else spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
