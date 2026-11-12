package com.savie.precut.ui.editor.timeline

import kotlinx.coroutines.launch
import com.savie.precut.ui.components.PreCutMotion
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * Two-way sync between the timeline scroll and the playhead time. A user drag (and its fling)
 * scrubs, between [onScrubStart] and [onScrubEnd]; otherwise the timeline follows [positionMs].
 * Scroll px == timeline px because the content is padded by half the viewport on each side,
 * putting time 0 under the centre playhead.
 *
 * Snapping (Phase 29D): while scrubbing, a time within [snapSlopPx] of one of [snapPoints] seeks to
 * that point exactly, with one light haptic tick on arrival; when the finger lifts and any fling
 * settles near it, the timeline springs onto it before the scrub ends.
 */
@Composable
fun ScrubSync(
    scrollState: ScrollState,
    positionMs: () -> Long,
    pxPerMs: Float,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    onSeek: (Long) -> Unit,
    snapPoints: () -> List<Long> = { emptyList() },
    snapSlopPx: Float = 0f,
) {
    val dragged by scrollState.interactionSource.collectIsDraggedAsState()
    var userScrolling by remember { mutableStateOf(false) }
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentSnapPoints by rememberUpdatedState(snapPoints)
    val haptics = LocalHapticFeedback.current
    val settleScope = rememberCoroutineScope()
    var snappedMs by remember { mutableStateOf<Long?>(null) }
    fun snapOf(px: Int): Long? =
        TimelineGeometry.snapEdge(TimelineGeometry.pxToMs(px.toFloat(), pxPerMs), currentSnapPoints(), (snapSlopPx / pxPerMs).toLong())

    // A scrub ends once the finger is up and any fling has settled; the two can change in either
    // order, so both are watched.
    LaunchedEffect(dragged, scrollState.isScrollInProgress) {
        when {
            dragged && !userScrolling -> {
                userScrolling = true
                currentOnScrubStart()
            }
            !dragged && !scrollState.isScrollInProgress && userScrolling -> {
                val target = snapOf(scrollState.value)?.let { TimelineGeometry.msToPx(it, pxPerMs).roundToInt() }
                if (target != null && target != scrollState.value) {
                    // Spring onto the snap point; this effect runs again when the spring settles.
                    settleScope.launch { scrollState.animateScrollTo(target, PreCutMotion.snap) }
                } else {
                    userScrolling = false
                    snappedMs = null
                    currentOnScrubEnd()
                }
            }
        }
    }
    LaunchedEffect(pxPerMs) {
        snapshotFlow { scrollState.value }.collect { px ->
            if (userScrolling) {
                val snap = snapOf(px)
                if (snap != null && snap != snappedMs) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                snappedMs = snap
                currentOnSeek(snap ?: TimelineGeometry.pxToMs(px.toFloat(), pxPerMs))
            }
        }
    }
    // Follows the playhead without recomposing: the snapshot flow re-reads it when it changes.
    LaunchedEffect(pxPerMs) {
        snapshotFlow { Triple(positionMs(), scrollState.maxValue, userScrolling) }.collectLatest { (position, _, scrolling) ->
            if (!scrolling) scrollState.scrollTo(TimelineGeometry.msToPx(position, pxPerMs).roundToInt())
        }
    }
}
