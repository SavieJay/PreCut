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
import kotlin.math.roundToInt

/**
 * Two-way sync between the timeline scroll and the playhead time. A user drag (and its fling)
 * seeks; otherwise the timeline follows [positionMs]. Scroll px == timeline px because the content
 * is padded by half the viewport on each side, putting time 0 under the centre playhead.
 */
@Composable
fun ScrubSync(
    scrollState: ScrollState,
    positionMs: Long,
    pxPerMs: Float,
    onScrubStart: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val dragged by scrollState.interactionSource.collectIsDraggedAsState()
    var userScrolling by remember { mutableStateOf(false) }
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnSeek by rememberUpdatedState(onSeek)

    LaunchedEffect(dragged) {
        if (dragged) {
            userScrolling = true
            currentOnScrubStart()
        }
    }
    LaunchedEffect(scrollState.isScrollInProgress) {
        // Ends after the fling that follows a drag settles.
        if (!scrollState.isScrollInProgress && !dragged) userScrolling = false
    }
    LaunchedEffect(pxPerMs) {
        snapshotFlow { scrollState.value }.collect { px ->
            if (userScrolling) currentOnSeek(TimelineGeometry.pxToMs(px.toFloat(), pxPerMs))
        }
    }
    LaunchedEffect(positionMs, pxPerMs, scrollState.maxValue) {
        if (!userScrolling) scrollState.scrollTo(TimelineGeometry.msToPx(positionMs, pxPerMs).roundToInt())
    }
}
