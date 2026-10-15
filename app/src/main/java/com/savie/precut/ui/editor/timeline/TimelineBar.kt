package com.savie.precut.ui.editor.timeline

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.theme.Accent
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
    /** The lane's 14 dp glyph, so lanes never rely on colour alone. */
    @DrawableRes glyph: Int? = null,
    /** White on the dark lane colours, Ink0 on the light ones (DESIGN.md "Timeline lanes"). */
    labelColor: Color = Color.White,
    /** Picked in multi-select: Accent outline and a check. */
    multiSelected: Boolean = false,
    /** Start/end handles; off for multi-select highlights. */
    showHandles: Boolean = selected,
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
    val autoScroller = LocalEdgeAutoScroller.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }

    fun rawMoveMs() = (moveDragPx / currentPxPerMs).roundToLong()

    fun dragNow() = BarDrag(
        moveMs = rawMoveMs().let { if (it == 0L) 0L else currentSnapMove(it) },
        startMs = (startDragPx / currentPxPerMs).roundToLong(),
        endMs = (endDragPx / currentPxPerMs).roundToLong(),
    )

    fun commit() {
        autoScroller?.stop()
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
            .then(
                when {
                    multiSelected -> Modifier.border(2.dp, Accent, BarShape)
                    selected || moving -> Modifier.border(2.dp, ClipSelection, BarShape)
                    else -> Modifier
                },
            )
            .onGloballyPositioned { coordinates[0] = it }
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
                    coordinates[0]?.takeIf { it.isAttached }?.let { coords ->
                        autoScroller?.onDrag(coords.localToWindow(change.position).x) { scrolled -> moveDragPx += scrolled }
                    }
                    val raw = rawMoveMs()
                    val nowSnapped = currentSnapMove(raw) != raw
                    if (nowSnapped && !snapped) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    snapped = nowSnapped
                }
            },
    ) {
        decoration(start, end)
        BarLabel(
            text = if (moving) formatPreciseMs(start) else label,
            glyph = glyph,
            color = labelColor,
            barStartPx = start * pxPerMs,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = if (showHandles) 14.dp else 6.dp),
        )
        if (multiSelected) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(16.dp)
                    .background(Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        if (showHandles) {
            TrimHandle(
                TrimSide.START,
                onDrag = { startDragPx += it }, onDragEnd = ::commit, modifier = Modifier.align(Alignment.CenterStart),
                width = 10.dp, gripHeight = 10.dp, shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
            )
            TrimHandle(
                TrimSide.END,
                onDrag = { endDragPx += it }, onDragEnd = ::commit, modifier = Modifier.align(Alignment.CenterEnd),
                width = 10.dp, gripHeight = 10.dp, shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
            )
        }
    }
}

/**
 * Glyph and label, kept at the visible left edge while the bar's start is scrolled off-screen
 * (read at placement, so scrolling never recomposes the bar).
 */
@Composable
private fun BarLabel(text: String, @DrawableRes glyph: Int?, color: Color, barStartPx: Float, modifier: Modifier = Modifier) {
    val viewport = LocalTimelineViewport.current
    Row(
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0))
            layout(placeable.width, placeable.height) {
                val hidden = viewport?.let { (it.visibleLeftPx() - barStartPx).coerceAtLeast(0f) } ?: 0f
                val room = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                placeable.place(hidden.roundToInt().coerceIn(0, room), 0)
            }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        glyph?.let { Icon(painterResource(it), contentDescription = null, tint = color, modifier = Modifier.size(14.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
