package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.theme.TextLaneBar
import kotlin.math.roundToLong

/**
 * Text overlays as bars from start to end. Drag any bar sideways to move it in time (it gets
 * selected); the selected bar also has handles for its start and end. Taps select.
 */
@Composable
fun TextLane(
    overlays: List<TextOverlay>,
    selectedTextId: String?,
    pxPerMs: Float,
    widthPx: Float,
    height: Dp,
    snap: (ownerId: String, startMs: Long, endMs: Long, moveMs: Long) -> Long,
    onSelect: (String) -> Unit,
    onTiming: (textId: String, startMs: Long, endMs: Long) -> Unit,
) {
    val density = LocalDensity.current
    val totalMs = (widthPx / pxPerMs).roundToLong()
    val placeholder = stringResource(R.string.text_placeholder)
    Box(Modifier.width(with(density) { widthPx.toDp() }).height(height)) {
        // The selected bar is drawn last so it sits on top of overlapping ones. key() keeps each
        // bar's state and in-flight gesture when selection changes this order.
        overlays.sortedBy { it.id == selectedTextId }.forEach { overlay ->
            key(overlay.id) {
                fun range(drag: BarDrag) = TimelineGeometry.dragTextRange(
                    overlay.startMs, overlay.endMs, drag.moveMs, drag.startMs, drag.endMs, totalMs,
                )
                TimelineBar(
                    id = overlay.id,
                    label = overlay.text.ifBlank { placeholder },
                    color = TextLaneBar,
                    selected = overlay.id == selectedTextId,
                    pxPerMs = pxPerMs,
                    rangeFor = ::range,
                    onSelect = { onSelect(overlay.id) },
                    onCommit = { drag -> range(drag).let { (start, end) -> onTiming(overlay.id, start, end) } },
                    snapMove = { move -> snap(overlay.id, overlay.startMs, overlay.endMs, move) },
                )
            }
        }
    }
}
