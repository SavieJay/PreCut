package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.TEXT_CORNER_RATIO
import com.savie.precut.media.TEXT_PADDING_RATIO
import com.savie.precut.media.textSizePx
import com.savie.precut.model.OutputSize
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextPrimary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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
    onDelete: () -> Unit,
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
                .dashedOutline(shape)
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
        // Corner handles turn with the text: ✕ deletes, the bottom-right one rotates and scales.
        fun corner(sx: Float, sy: Float): Offset = rotate(Offset(sx * textSize.x / 2, sy * textSize.y / 2), shown.rotationDegrees) + center
        FrameHandle(R.drawable.ic_close, stringResource(R.string.text_delete), corner(-1f, -1f)) { Modifier.clickable(role = Role.Button, onClick = onDelete) }
        FrameHandle(R.drawable.ic_open_in_full, stringResource(R.string.text_rotate_scale), corner(1f, 1f)) {
            Modifier.pointerInput(overlay.id) {
                var origin = currentStart
                var from = Offset.Zero
                var dragged = Offset.Zero
                var latest: Pair<TextTransform, TextTransform.Snaps>? = null
                detectDragGestures(
                    onDragStart = {
                        origin = currentStart
                        from = rotate(Offset(textSize.x / 2, textSize.y / 2), origin.rotationDegrees)
                        dragged = Offset.Zero
                    },
                    onDragEnd = {
                        latest?.let { currentOnTransformed(it.first) }
                        latest = null
                        live = null
                    },
                    onDragCancel = { live = null },
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                    val to = from + dragged
                    val zoom = if (from.getDistance() > 0f) to.getDistance() / from.getDistance() else 1f
                    val turn = Math.toDegrees((atan2(to.y, to.x) - atan2(from.y, from.x)).toDouble()).toFloat()
                    latest = TextTransform.applyGesture(origin, 0f, 0f, zoom, turn, 0f).also { live = it }
                }
            }
        }
        if (!active) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .height(32.dp)
                    .background(Color(0xD90E0E10), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(painterResource(R.drawable.ic_pinch), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.text_gesture_hint), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            }
        }
    }
}

private fun TextTransform.Snaps.any() = centreX || centreY || rotation

/** The text box's 1.5 dp dashed white outline. */
private fun Modifier.dashedOutline(shape: RoundedCornerShape): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, Color.White, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}
