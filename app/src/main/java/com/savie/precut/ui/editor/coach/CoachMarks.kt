package com.savie.precut.ui.editor.coach

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** What the editor's first-run tips (Phase 24) point at, in order. */
enum class CoachTarget(@StringRes val tip: Int) {
    TIMELINE(R.string.coach_timeline),
    TOOLS(R.string.coach_tools),
    EXPORT(R.string.coach_export),
}

/** Where each target is on screen, as laid out. */
class CoachTargets {
    val bounds = mutableStateMapOf<CoachTarget, Rect>()
}

/** Provided by the editor screen; null elsewhere (previews). */
val LocalCoachTargets = staticCompositionLocalOf<CoachTargets?> { null }

/** Records this element as [target] for the tips (nothing without [targets]). */
fun Modifier.coachTarget(targets: CoachTargets?, target: CoachTarget): Modifier =
    if (targets == null) this else onGloballyPositioned { targets.bounds[target] = it.boundsInRoot() }

/**
 * The first-run tips: the screen dimmed with a cut-out around each target in turn and a bubble
 * saying what it does, "1 of 3". A tap anywhere moves on; after the last, [onDone].
 */
@Composable
fun CoachMarksOverlay(targets: CoachTargets, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val target = CoachTarget.entries[step]
    val rect = targets.bounds[target] ?: return
    val density = LocalDensity.current
    val next = { if (step == CoachTarget.entries.lastIndex) onDone() else step++ }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(step) { detectTapGestures { next() } },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            val pad = 6.dp.toPx()
            drawRoundRect(
                Color.Black,
                topLeft = Offset(rect.left - pad, rect.top - pad),
                size = rect.inflate(pad).size,
                cornerRadius = CornerRadius(12.dp.toPx()),
                blendMode = BlendMode.Clear,
            )
        }
        // The bubble goes on the side of the target with more room.
        val screenHeight = with(density) { maxHeight.toPx() }
        val below = rect.center.y < screenHeight / 2
        val bubbleY = if (below) rect.bottom + with(density) { 16.dp.toPx() } else rect.top - with(density) { 140.dp.toPx() }
        Box(Modifier.fillMaxWidth().offset { IntOffset(0, bubbleY.roundToInt().coerceAtLeast(0)) }.padding(horizontal = 24.dp)) {
            Column(
                Modifier.fillMaxWidth().background(Ink2, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(target.tip), style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.coach_step, step + 1, CoachTarget.entries.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(if (step == CoachTarget.entries.lastIndex) R.string.coach_done else R.string.coach_next),
                        style = MaterialTheme.typography.labelLarge,
                        color = Accent,
                    )
                }
            }
        }
    }
}
