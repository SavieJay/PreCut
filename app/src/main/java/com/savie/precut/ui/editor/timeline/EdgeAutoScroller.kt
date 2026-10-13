package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Scrolls the timeline while a drag (trim handle, moved bar) is held near the viewport's edges, so
 * items can be dragged beyond what's on screen. Each frame's scroll is reported to the drag, which
 * adds it so the dragged edge stays under the finger.
 */
class EdgeAutoScroller(
    private val scrollState: ScrollState,
    private val scope: CoroutineScope,
    private val edgePx: Float,
    private val maxSpeedPx: Float,
) {
    /** Viewport position and width in window px; kept up to date by the timeline. */
    var viewportLeftPx = 0f
    var viewportWidthPx = 0f

    /** Moves the playhead with the scroll (scroll px == timeline px), like a user scrub. */
    var onScrollTo: (scrollPx: Int) -> Unit = {}

    /** Called once auto-scrolling stops, to end the scrub. */
    var onScrollEnd: () -> Unit = {}

    private var job: Job? = null
    private var speed = 0f
    private var onScrolled: (Float) -> Unit = {}

    /** Call on every drag event with the pointer's x in window px. */
    fun onDrag(pointerWindowX: Float, onScrolled: (Float) -> Unit) {
        speed = TimelineGeometry.autoScrollSpeed(pointerWindowX - viewportLeftPx, viewportWidthPx, edgePx, maxSpeedPx)
        this.onScrolled = onScrolled
        if (speed == 0f) return stop()
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive && speed != 0f) {
                withFrameNanos { }
                val scrolled = scrollState.scrollBy(speed)
                if (scrolled == 0f) break // reached either end
                this@EdgeAutoScroller.onScrolled(scrolled)
                onScrollTo(scrollState.value)
            }
        }
    }

    fun stop() {
        val wasScrolling = job != null
        job?.cancel()
        job = null
        speed = 0f
        if (wasScrolling) onScrollEnd()
    }
}

/** The timeline's auto-scroller, or null outside a timeline (previews). */
val LocalEdgeAutoScroller = compositionLocalOf<EdgeAutoScroller?> { null }
