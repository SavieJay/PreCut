package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Two-finger pinch reports zoom factors. Runs in the Initial pass and consumes only multi-touch
 * events, so one-finger scrolling of the child still works.
 */
@Composable
fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier {
    val currentOnZoom by rememberUpdatedState(onZoom)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) currentOnZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

/** Callbacks for [tapAndLongPressDrag]; positions are in the modifier's local coordinates. */
interface TapAndDragCallbacks {
    fun onTap(position: Offset)

    /** Returns false to ignore this long press (e.g. not over a clip). */
    fun onLongPressStart(position: Offset): Boolean
    fun onLongPressDrag(position: Offset)
    fun onLongPressEnd()
}

private class UpResult(val up: PointerInputChange?)

/**
 * One-finger tap and long-press-then-drag, detected on a parent so the gesture survives the
 * children being re-laid out (the timeline switches to reorder tiles mid-gesture). A child that
 * consumes events first (scrolling, trim handles) cancels both.
 */
@Composable
fun Modifier.tapAndLongPressDrag(callbacks: TapAndDragCallbacks): Modifier {
    val current by rememberUpdatedState(callbacks)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                UpResult(waitForUpOrCancellation())
            }
            when {
                result?.up != null -> current.onTap(result.up.position)
                result != null -> Unit // cancelled: a child consumed the gesture
                current.onLongPressStart(down.position) -> {
                    // After the long press, take every event before children so nothing scrolls.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            change?.consume()
                            current.onLongPressEnd()
                            break
                        }
                        current.onLongPressDrag(change.position)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}
