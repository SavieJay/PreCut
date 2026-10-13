package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.TEXT_CORNER_RATIO
import com.savie.precut.media.TEXT_PADDING_RATIO
import com.savie.precut.media.textSizePx
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.outputSizeFor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Drag handle for the selected text over the preview. Idle, it outlines where the burned-in text
 * is; while dragging it shows the text itself. Sizes follow the export's rules (TextOverlayRenderer).
 */
@Composable
fun TextPositionOverlay(
    overlay: TextOverlay,
    aspectRatio: AspectRatio,
    onMoved: (xNorm: Float, yNorm: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnMoved by rememberUpdatedState(onMoved)
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The letterboxed video frame inside the preview (ContentScale.Fit), in px.
        val output = outputSizeFor(aspectRatio)
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / output.width, boxH / output.height)
        val frameW = output.width * scale
        val frameH = output.height * scale
        val left = (boxW - frameW) / 2
        val top = (boxH - frameH) / 2

        var drag by remember(overlay.id, overlay.xNorm, overlay.yNorm) { mutableStateOf<Offset?>(null) }
        // Read at drag end, so a long-lived gesture never uses stale geometry or position.
        val start by rememberUpdatedState(Offset(overlay.xNorm, overlay.yNorm))
        val frameSize by rememberUpdatedState(Offset(frameW, frameH))
        val centerX = left + overlay.xNorm * frameW + (drag?.x ?: 0f)
        val centerY = top + overlay.yNorm * frameH + (drag?.y ?: 0f)

        val density = LocalDensity.current
        val textPx = textSizePx(overlay.textSizeSp, min(frameW, frameH).roundToInt())
        val padding = with(density) { (textPx * TEXT_PADDING_RATIO).toDp() }
        val shape = RoundedCornerShape(with(density) { (textPx * TEXT_CORNER_RATIO).toDp() })
        val dragging = drag != null
        val background = overlay.backgroundArgb?.takeIf { dragging }?.let(::Color) ?: Color.Transparent

        Text(
            text = overlay.text.ifBlank { stringResource(R.string.text_placeholder) },
            color = if (dragging || overlay.text.isBlank()) Color(overlay.colorArgb).copy(alpha = 0.85f) else Color.Transparent,
            fontSize = with(density) { textPx.toSp() },
            textAlign = TextAlign.Center,
            modifier = Modifier
                .layout { measurable, _ ->
                    val maxWidth = (frameW * 0.9f + 2 * padding.toPx()).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints(maxWidth = maxWidth))
                    layout(placeable.width, placeable.height) {
                        placeable.place((centerX - placeable.width / 2f).roundToInt(), (centerY - placeable.height / 2f).roundToInt())
                    }
                }
                .pointerInput(overlay.id) {
                    detectDragGestures(
                        onDragStart = { drag = Offset.Zero },
                        onDragEnd = {
                            val moved = drag ?: Offset.Zero
                            drag = null
                            currentOnMoved(
                                (start.x + moved.x / frameSize.x).coerceIn(0f, 1f),
                                (start.y + moved.y / frameSize.y).coerceIn(0f, 1f),
                            )
                        },
                        onDragCancel = { drag = null },
                    ) { change, amount ->
                        change.consume()
                        drag = (drag ?: Offset.Zero) + amount
                    }
                }
                .border(1.dp, Color.White.copy(alpha = 0.9f), shape)
                .background(background, shape)
                .padding(padding),
        )
    }
}
