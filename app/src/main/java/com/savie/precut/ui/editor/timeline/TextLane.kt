package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.theme.ClipSelection
import com.savie.precut.ui.theme.TextLaneBar
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val BarShape = RoundedCornerShape(4.dp)

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
    onSelect: (String) -> Unit,
    onTiming: (textId: String, startMs: Long, endMs: Long) -> Unit,
) {
    val density = LocalDensity.current
    val totalMs = (widthPx / pxPerMs).roundToLong()
    Box(Modifier.width(with(density) { widthPx.toDp() }).height(height)) {
        // The selected bar is drawn last so it sits on top of overlapping ones. key() keeps each
        // bar's state and in-flight gesture when selection changes this order.
        overlays.sortedBy { it.id == selectedTextId }.forEach { overlay ->
            key(overlay.id) {
                TextBar(overlay, overlay.id == selectedTextId, pxPerMs, totalMs, onSelect, onTiming)
            }
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
    // Gesture callbacks outlive compositions, so they read these instead of captured values.
    val currentOverlay by rememberUpdatedState(overlay)
    val currentSelected by rememberUpdatedState(selected)
    val currentPxPerMs by rememberUpdatedState(pxPerMs)
    val currentTotalMs by rememberUpdatedState(totalMs)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTiming by rememberUpdatedState(onTiming)

    // Pending drags in px; shown live and committed once on release.
    var moveDragPx by remember { mutableFloatStateOf(0f) }
    var startDragPx by remember { mutableFloatStateOf(0f) }
    var endDragPx by remember { mutableFloatStateOf(0f) }
    var moving by remember { mutableStateOf(false) }

    fun rangeNow(): Pair<Long, Long> = TimelineGeometry.dragTextRange(
        startMs = currentOverlay.startMs,
        endMs = currentOverlay.endMs,
        moveDeltaMs = (moveDragPx / currentPxPerMs).roundToLong(),
        startDeltaMs = (startDragPx / currentPxPerMs).roundToLong(),
        endDeltaMs = (endDragPx / currentPxPerMs).roundToLong(),
        totalMs = currentTotalMs,
    )

    fun commit() {
        val (newStart, newEnd) = rangeNow()
        moveDragPx = 0f
        startDragPx = 0f
        endDragPx = 0f
        moving = false
        if (newStart != currentOverlay.startMs || newEnd != currentOverlay.endMs) {
            currentOnTiming(currentOverlay.id, newStart, newEnd)
        }
    }

    val (start, end) = rangeNow()
    Box(
        Modifier
            .offset { IntOffset((start * pxPerMs).roundToInt(), 0) }
            .width(with(density) { ((end - start) * pxPerMs).toDp() })
            .fillMaxHeight()
            .clip(BarShape)
            .background(TextLaneBar)
            .then(if (selected || moving) Modifier.border(1.5.dp, ClipSelection, BarShape) else Modifier)
            .pointerInput(overlay.id) { detectTapGestures(onTap = { currentOnSelect(currentOverlay.id) }) }
            .pointerInput(overlay.id) {
                // Keyed on the id only: selecting the bar mid-drag must not restart the gesture.
                detectHorizontalDragGestures(
                    onDragStart = {
                        moving = true
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        if (!currentSelected) currentOnSelect(currentOverlay.id)
                    },
                    onDragEnd = { commit() },
                    onDragCancel = { commit() },
                ) { change, amount ->
                    change.consume() // the timeline must not scroll while a tile moves
                    moveDragPx += amount
                }
            },
    ) {
        Text(
            text = if (moving) formatPreciseMs(start) else overlay.text.ifBlank { stringResource(R.string.text_placeholder) },
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
