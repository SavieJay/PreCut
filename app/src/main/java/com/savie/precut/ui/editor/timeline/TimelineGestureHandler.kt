package com.savie.precut.ui.editor.timeline

import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.savie.precut.model.Clip

/**
 * Tap-to-select and long-press-to-reorder for the timeline, with haptic ticks as the drop slot
 * changes. [reorder] is the timeline's reorder state, shared with the overlay.
 */
class TimelineGestureHandler(
    private val clips: List<Clip>,
    private val hitTest: () -> TimelineHitTest,
    private val reorderLayout: ReorderLayout,
    private val reorder: MutableState<ReorderState?>,
    private val haptics: HapticFeedback,
    private val actions: TimelineActions,
) : TapAndDragCallbacks {

    override fun onTap(position: Offset) =
        actions.onSelectClip(hitTest().clipIndexAt(position.x)?.let { clips[it].id })

    override fun onLongPressStart(position: Offset): Boolean {
        if (clips.size < 2) return false
        val hit = hitTest()
        if (hit.isOnTrimHandle(position.x)) return false
        val index = hit.clipIndexAt(position.x) ?: return false
        reorder.value = ReorderState(index, position.x)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
    }

    override fun onLongPressDrag(position: Offset) {
        val state = reorder.value ?: return
        if (reorderLayout.targetIndex(position.x, clips.size) != reorderLayout.targetIndex(state.fingerX, clips.size)) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        reorder.value = state.copy(fingerX = position.x)
    }

    override fun onLongPressEnd() {
        val state = reorder.value ?: return
        reorder.value = null
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
        actions.onMove(state.fromIndex, reorderLayout.targetIndex(state.fingerX, clips.size))
    }
}
