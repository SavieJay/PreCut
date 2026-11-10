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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.InverseAccent
import com.savie.precut.ui.theme.InverseSurface
import com.savie.precut.ui.theme.tabular
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
 * The first-run tips (design O-06): the screen dimmed with an accent-ringed cut-out around each
 * target in turn, and a light bubble pointing at it with what it does, "1 of 3" and Next. A tap
 * anywhere also moves on; after the last, [onDone].
 */
@Composable
fun CoachMarksOverlay(targets: CoachTargets, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var bubbleHeight by remember { mutableIntStateOf(0) }
    val target = CoachTarget.entries[step]
    val rect = targets.bounds[target] ?: return
    val density = LocalDensity.current
    val next: () -> Unit = { if (step == CoachTarget.entries.lastIndex) onDone() else step++ }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(step) { detectTapGestures { next() } },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color(0xFF08080A).copy(alpha = 0.74f))
            val pad = 6.dp.toPx()
            val topLeft = Offset(rect.left - pad, rect.top - pad)
            val size = rect.inflate(pad).size
            val corner = CornerRadius(14.dp.toPx())
            drawRoundRect(Color.Black, topLeft = topLeft, size = size, cornerRadius = corner, blendMode = BlendMode.Clear)
            drawRoundRect(Accent, topLeft = topLeft, size = size, cornerRadius = corner, style = Stroke(2.dp.toPx()))
        }
        // The bubble goes on the side of the target with more room, its pointer toward the target.
        val screenHeight = with(density) { maxHeight.toPx() }
        val below = rect.center.y < screenHeight / 2
        val gap = with(density) { 18.dp.toPx() }
        val bubbleY = if (below) rect.bottom + gap else rect.top - gap - bubbleHeight
        val side = with(density) { BUBBLE_MARGIN.toPx() }
        val pointerX = with(density) { (rect.center.x - side).toDp() - POINTER_SIZE / 2 }
            .coerceIn(16.dp, (maxWidth - BUBBLE_MARGIN * 2 - POINTER_SIZE - 16.dp).coerceAtLeast(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, bubbleY.roundToInt().coerceAtLeast(0)) }
                .padding(horizontal = BUBBLE_MARGIN)
                .onSizeChanged { bubbleHeight = it.height },
        ) {
            Box(
                Modifier
                    .align(if (below) Alignment.TopStart else Alignment.BottomStart)
                    .offset(x = pointerX, y = if (below) -POINTER_SIZE / 2 else POINTER_SIZE / 2)
                    .size(POINTER_SIZE)
                    .rotate(45f)
                    .background(InverseSurface, RoundedCornerShape(2.dp)),
            )
            Column(
                Modifier.fillMaxWidth().background(InverseSurface, RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(target.tip), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Ink0, modifier = Modifier.padding(end = 8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.coach_step, step + 1, CoachTarget.entries.size),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = Ink0.copy(alpha = 0.72f),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = next) {
                        Text(
                            stringResource(if (step == CoachTarget.entries.lastIndex) R.string.coach_done else R.string.coach_next),
                            style = MaterialTheme.typography.labelLarge,
                            color = InverseAccent,
                        )
                    }
                }
            }
        }
    }
}

/** The bubble's distance from the screen's sides, and its pointer's size. */
private val BUBBLE_MARGIN = 24.dp
private val POINTER_SIZE = 14.dp
