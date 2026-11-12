package com.savie.precut.ui.editor.coach

import androidx.compose.foundation.layout.Spacer
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.components.AccentTextButton
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentPill
import com.savie.precut.ui.theme.GlassLine
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import kotlin.math.roundToInt
import com.savie.precut.ui.theme.CoachDim

/**
 * The first-run tips (RD-O06): the screen dimmed with a white-ringed cut-out around each target
 * in turn, and a solid dark bubble (26 dp corners) pointing at it with what it does, "1 of 3" and
 * a small pink Next pill. A tap anywhere also moves on; after the last, [onDone].
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
            drawRect(CoachDim)
            val pad = 6.dp.toPx()
            val topLeft = Offset(rect.left - pad, rect.top - pad)
            val size = rect.inflate(pad).size
            val corner = CornerRadius(20.dp.toPx())
            drawRoundRect(Color.Black, topLeft = topLeft, size = size, cornerRadius = corner, blendMode = BlendMode.Clear)
            // RD-O06: a white ring with a soft halo around the cut-out.
            drawRoundRect(SpotHalo, topLeft = topLeft, size = size, cornerRadius = corner, style = Stroke(8.dp.toPx()))
            drawRoundRect(SpotRing, topLeft = topLeft, size = size, cornerRadius = corner, style = Stroke(2.dp.toPx()))
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
                    .background(BubbleFill, RoundedCornerShape(2.dp)),
            )
            // Solid, not glass: the tips sit over the editor's video preview.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(BubbleFill, BubbleShape)
                    .border(1.dp, GlassLine, BubbleShape)
                    .padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(target.tip), style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.padding(end = 6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // RD-O06: "Skip tips" ends them at once, the same as finishing them.
                    if (step < CoachTarget.entries.lastIndex) AccentTextButton(stringResource(R.string.coach_skip), onDone, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.coach_step, step + 1, CoachTarget.entries.size), style = PreCutType.timecode, color = TextMuted)
                    AccentPill(
                        stringResource(if (step == CoachTarget.entries.lastIndex) R.string.coach_done else R.string.coach_next),
                        enabled = true,
                        onClick = next,
                    )
                }
            }
        }
    }
}

/** The bubble's distance from the screen's sides, and its pointer's size. */
private val BUBBLE_MARGIN = 16.dp
private val POINTER_SIZE = 14.dp

private val BubbleFill = Ink3
private val BubbleShape = RoundedCornerShape(26.dp)
private val SpotRing = Color.White.copy(alpha = 0.85f)
private val SpotHalo = Color.White.copy(alpha = 0.12f)
