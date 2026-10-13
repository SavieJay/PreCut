package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.theme.ClipSelection
import com.savie.precut.ui.theme.TextLaneBar
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val BarShape = RoundedCornerShape(4.dp)

/**
 * Text overlays as bars from start to end. The selected bar can be dragged to move it in time and
 * has handles for its start/end; any bar can be long-pressed and dragged (which also selects it).
 */
@Composable
fun TextLane(
    overlays: List<TextOverlay>,
    selectedTextId: String?,
    pxPerMs: Float,
    widthPx: Float,
    height: Dp,
    onSelect: (String) -> Unit,
    onTiming: (textId: String, startMs: Long, endMs: Long) -> Unit,
) {
    val density = LocalDensity.current
    Box(Modifier.width(with(density) { widthPx.toDp() }).height(height)) {
        // The selected bar is drawn last so it sits on top of overlapping ones.
        overlays.sortedBy { it.id == selectedTextId }.forEach { overlay ->
            TextBar(overlay, overlay.id == selectedTextId, pxPerMs, totalMs = (widthPx / pxPerMs).roundToLong(), onSelect, onTiming)
        }
    }
}

@Composable
private fun TextBar(
    overlay: TextOverlay,
    selected: Boolean,
    pxPerMs: Float,
    totalMs: Long,
    onSelect: (String) -> Unit,
    onTiming: (textId: String, startMs: Long, endMs: Long) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    // Pending drags in px, applied as new timing on release.
    var startDragPx by remember(overlay.id) { mutableFloatStateOf(0f) }
    var endDragPx by remember(overlay.id) { mutableFloatStateOf(0f) }
    var moveDragPx by remember(overlay.id) { mutableFloatStateOf(0f) }
    val (movedStart, movedEnd) = TimelineGeometry.moveRange(
        overlay.startMs, overlay.endMs, (moveDragPx / pxPerMs).roundToLong(), totalMs,
    )
    val start = (movedStart + startDragPx / pxPerMs).roundToLong().coerceIn(0L, movedEnd)
    val end = (movedEnd + endDragPx / pxPerMs).roundToLong().coerceIn(start, totalMs.coerceAtLeast(start))
    fun commit() {
        startDragPx = 0f
        endDragPx = 0f
        moveDragPx = 0f
        onTiming(overlay.id, start, end)
    }
    val currentCommit by rememberUpdatedState(::commit)

    Box(
        Modifier
            .offset { IntOffset((start * pxPerMs).roundToInt(), 0) }
            .width(with(density) { ((end - start) * pxPerMs).toDp() })
            .fillMaxHeight()
            .clip(BarShape)
            .background(TextLaneBar)
            .then(if (selected) Modifier.border(1.5.dp, ClipSelection, BarShape) else Modifier)
            .pointerInput(overlay.id) { detectTapGestures(onTap = { currentOnSelect(overlay.id) }) }
            .pointerInput(overlay.id, selected) {
                if (selected) {
                    // Selected: a plain horizontal drag on the body moves the bar (handles resize it).
                    detectHorizontalDragGestures(
                        onDragEnd = { currentCommit() },
                        onDragCancel = { currentCommit() },
                    ) { change, amount ->
                        change.consume()
                        moveDragPx += amount
                    }
                } else {
                    // Not selected: long-press first, so a normal drag still scrolls the timeline.
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnSelect(overlay.id)
                        },
                        onDragEnd = { currentCommit() },
                        onDragCancel = { currentCommit() },
                    ) { change, amount ->
                        change.consume()
                        moveDragPx += amount.x
                    }
                }
            },
    ) {
        Text(
            text = overlay.text.ifBlank { stringResource(R.string.text_placeholder) },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = if (selected) 16.dp else 6.dp),
        )
        if (selected) {
            TrimHandle(onDrag = { startDragPx += it }, onDragEnd = ::commit, modifier = Modifier.align(Alignment.CenterStart))
            TrimHandle(onDrag = { endDragPx += it }, onDragEnd = ::commit, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}
