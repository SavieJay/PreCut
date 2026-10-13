package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.TEXT_CORNER_RATIO
import com.savie.precut.media.TEXT_PADDING_RATIO
import com.savie.precut.media.textSizePx
import com.savie.precut.model.OutputSize
import com.savie.precut.model.TextOverlay
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Transform handle for the selected text over the preview. One finger moves it (when the touch
 * starts on the text); two fingers anywhere pinch to scale and twist to rotate. Snaps to the centre
 * lines and right angles. Idle, it outlines the burned-in text; while transforming it shows a
 * ghost. The result is committed on release. Sizes follow TextOverlayRenderer.
 */
@Composable
fun TextTransformOverlay(
    overlay: TextOverlay,
    outputSize: OutputSize,
    onTransformed: (TextTransform) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnTransformed by rememberUpdatedState(onTransformed)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val frame = FrameRect.fitting(outputSize, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val start = TextTransform.of(overlay)
        var live by remember(overlay) { mutableStateOf<Pair<TextTransform, TextTransform.Snaps>?>(null) }
        var textSize by remember { mutableStateOf(Offset.Zero) }
        val shown = live?.first ?: start
        val snaps = live?.second
        val currentStart by rememberUpdatedState(start)
        val currentFrame by rememberUpdatedState(frame)
        val snapNorm = with(density) { 8.dp.toPx() } / min(frame.width, frame.height)

        val textPx = textSizePx(shown.sizeSp, min(frame.width, frame.height).roundToInt())
        val padding = with(density) { (textPx * TEXT_PADDING_RATIO).toDp() }
        val shape = RoundedCornerShape(with(density) { (textPx * TEXT_CORNER_RATIO).toDp() })
        val active = live != null
        val center = frame.toPx(shown.xNorm, shown.yNorm)

        Text(
            text = overlay.text.ifBlank { stringResource(R.string.text_placeholder) },
            color = if (active || overlay.text.isBlank()) Color(overlay.colorArgb).copy(alpha = 0.85f * overlay.opacity) else Color.Transparent,
            fontSize = with(density) { textPx.toSp() },
            textAlign = TextAlign.Center,
            modifier = Modifier
                .centredAt(center, maxWidthPx = frame.width * 0.9f + 2 * with(density) { padding.toPx() }) { textSize = it }
                .graphicsLayer { rotationZ = shown.rotationDegrees }
                .border(1.dp, Color.White.copy(alpha = 0.9f), shape)
                .background(overlay.backgroundArgb?.takeIf { active }?.let { Color(it).copy(alpha = Color(it).alpha * overlay.opacity) } ?: Color.Transparent, shape)
                .padding(padding),
        )
        // On top of the text so it receives every touch on the preview.
        Box(
            Modifier
            .fillMaxSize()
            .drawBehind { if (snaps != null) drawGuides(frame, snaps) }
            .pointerInput(overlay.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val origin = currentStart
                    val onText = hitsText(down.position, currentFrame.toPx(origin.xNorm, origin.yNorm), textSize, origin.rotationDegrees, 24.dp.toPx())
                    var pan = Offset.Zero
                    var zoom = 1f
                    var rotation = 0f
                    var latest: Pair<TextTransform, TextTransform.Snaps>? = null
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (onText || pressed >= 2) {
                            pan += event.calculatePan()
                            zoom *= event.calculateZoom()
                            if (pressed >= 2) rotation += event.calculateRotation()
                            val next = TextTransform.applyGesture(
                                origin, pan.x / currentFrame.width, pan.y / currentFrame.height, zoom, rotation, snapNorm,
                            )
                            if (next.second != latest?.second && next.second.any()) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            latest = next
                            live = next
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    latest?.let { currentOnTransformed(it.first) }
                    live = null
                }
            },
        )
    }
}

private fun TextTransform.Snaps.any() = centreX || centreY || rotation
