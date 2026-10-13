package com.savie.precut.ui.editor.timeline

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
 */
@Composable
fun ScrubSync(
    scrollState: ScrollState,
    positionMs: () -> Long,
    pxPerMs: Float,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val dragged by scrollState.interactionSource.collectIsDraggedAsState()
    var userScrolling by remember { mutableStateOf(false) }
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val currentOnSeek by rememberUpdatedState(onSeek)

    // A scrub ends once the finger is up and any fling has settled; the two can change in either
    // order, so both are watched.
    LaunchedEffect(dragged, scrollState.isScrollInProgress) {
        when {
            dragged && !userScrolling -> {
                userScrolling = true
                currentOnScrubStart()
            }
            !dragged && !scrollState.isScrollInProgress && userScrolling -> {
                userScrolling = false
                currentOnScrubEnd()
            }
        }
    }
    LaunchedEffect(pxPerMs) {
        snapshotFlow { scrollState.value }.collect { px ->
            if (userScrolling) currentOnSeek(TimelineGeometry.pxToMs(px.toFloat(), pxPerMs))
        }
    }
    // Follows the playhead without recomposing: the snapshot flow re-reads it when it changes.
    LaunchedEffect(pxPerMs) {
        snapshotFlow { Triple(positionMs(), scrollState.maxValue, userScrolling) }.collectLatest { (position, _, scrolling) ->
            if (!scrolling) scrollState.scrollTo(TimelineGeometry.msToPx(position, pxPerMs).roundToInt())
        }
    }
}
