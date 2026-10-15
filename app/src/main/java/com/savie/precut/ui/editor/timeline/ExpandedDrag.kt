package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.TouchBoundsExpansion
import androidx.compose.ui.unit.IntSize

/**
 * Horizontal drags that also start up to [expandLeftPx] / [expandRightPx] beyond the element's
 * left and right edges: a finger-sized grab area for a thin handle without changing how it looks.
 */
fun Modifier.expandedHorizontalDrag(
    expandLeftPx: Int,
    expandRightPx: Int,
    onEnd: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Float) -> Unit,
): Modifier = this then ExpandedDragElement(expandLeftPx, expandRightPx, onDrag, onEnd)

private data class ExpandedDragElement(
    val expandLeftPx: Int,
    val expandRightPx: Int,
    val onDrag: (PointerInputChange, Float) -> Unit,
    val onEnd: () -> Unit,
) : ModifierNodeElement<ExpandedDragNode>() {
    override fun create() = ExpandedDragNode(expandLeftPx, expandRightPx, onDrag, onEnd)

    override fun update(node: ExpandedDragNode) {
        node.expandLeftPx = expandLeftPx
        node.expandRightPx = expandRightPx
        node.onDrag = onDrag
        node.onEnd = onEnd
    }
}

/** Forwards events to a delegated gesture detector and widens its touch bounds (as `clickable` does). */
private class ExpandedDragNode(
    var expandLeftPx: Int,
    var expandRightPx: Int,
    var onDrag: (PointerInputChange, Float) -> Unit,
    var onEnd: () -> Unit,
) : DelegatingNode(), PointerInputModifierNode {

    private val input = delegate(
        SuspendingPointerInputModifierNode {
            detectHorizontalDragGestures(onDragEnd = { onEnd() }, onDragCancel = { onEnd() }) { change, amount -> onDrag(change, amount) }
        },
    )

    override val touchBoundsExpansion: TouchBoundsExpansion
        get() = TouchBoundsExpansion.Absolute(left = expandLeftPx.coerceAtLeast(0), right = expandRightPx.coerceAtLeast(0))

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) =
        input.onPointerEvent(pointerEvent, pass, bounds)

    override fun onCancelPointerInput() = input.onCancelPointerInput()
}
