package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.theme.ClipSelection
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val BarShape = RoundedCornerShape(4.dp)

/** Pending drags of a [TimelineBar], in ms: body move and start/end handle deltas. */
data class BarDrag(val moveMs: Long = 0L, val startMs: Long = 0L, val endMs: Long = 0L)

/**
 * A bar on a timeline lane (text, music). Drag the body sideways to move it (selecting it first if
 * needed); the selected bar has start/end handles. Drags show live via [rangeFor] and are reported
 * once on release via [onCommit]. Gestures are keyed on [id] only, so selection changes mid-drag
 * don't restart them.
 */
@Composable
fun TimelineBar(
    id: String,
    label: String,
    color: Color,
    selected: Boolean,
    pxPerMs: Float,
    /** Timeline start/end to draw for the given pending drag. */
    rangeFor: (BarDrag) -> Pair<Long, Long>,
    onSelect: () -> Unit,
    onCommit: (BarDrag) -> Unit,
    /** Adjusts a raw body-move delta (ms) to snap to beats/playhead/edges; identity by default. */
    snapMove: (Long) -> Long = { it },
    decoration: @Composable BoxScope.(startMs: Long, endMs: Long) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentSelected by rememberUpdatedState(selected)
    val currentPxPerMs by rememberUpdatedState(pxPerMs)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentSnapMove by rememberUpdatedState(snapMove)

    var moveDragPx by remember { mutableFloatStateOf(0f) }
    var startDragPx by remember { mutableFloatStateOf(0f) }
    var endDragPx by remember { mutableFloatStateOf(0f) }
    var moving by remember { mutableStateOf(false) }
    var snapped by remember { mutableStateOf(false) }

    fun rawMoveMs() = (moveDragPx / currentPxPerMs).roundToLong()

    fun dragNow() = BarDrag(
        moveMs = rawMoveMs().let { if (it == 0L) 0L else currentSnapMove(it) },
        startMs = (startDragPx / currentPxPerMs).roundToLong(),
        endMs = (endDragPx / currentPxPerMs).roundToLong(),
    )

    fun commit() {
        val drag = dragNow()
        moveDragPx = 0f
        startDragPx = 0f
        endDragPx = 0f
        moving = false
        snapped = false
        if (drag != BarDrag()) currentOnCommit(drag)
    }

    val (start, end) = rangeFor(dragNow())
    Box(
        Modifier
            .offset { IntOffset((start * pxPerMs).roundToInt(), 0) }
            .width(with(density) { ((end - start) * pxPerMs).toDp() })
            .fillMaxHeight()
            .clip(BarShape)
            .background(color)
            .then(if (selected || moving) Modifier.border(1.5.dp, ClipSelection, BarShape) else Modifier)
            .pointerInput(id) { detectTapGestures(onTap = { currentOnSelect() }) }
            .pointerInput(id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        moving = true
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        if (!currentSelected) currentOnSelect()
                    },
                    onDragEnd = { commit() },
                    onDragCancel = { commit() },
                ) { change, amount ->
                    change.consume() // the timeline must not scroll while a bar moves
                    moveDragPx += amount
                    val raw = rawMoveMs()
                    val nowSnapped = currentSnapMove(raw) != raw
                    if (nowSnapped && !snapped) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    snapped = nowSnapped
                }
            },
    ) {
        decoration(start, end)
        Text(
            text = if (moving) formatPreciseMs(start) else label,
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
