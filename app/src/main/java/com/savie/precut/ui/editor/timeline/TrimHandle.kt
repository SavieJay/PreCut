package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.ClipSelection
import com.savie.precut.ui.theme.Ink0

/**
 * White grip on a selected item's edge; reports horizontal drag deltas in px, including the
 * timeline's auto-scroll when held near the screen edge.
 */
@Composable
fun TrimHandle(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    /** 14 dp for clips, 10 dp for bars. */
    width: Dp = 14.dp,
    gripHeight: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val autoScroller = LocalEdgeAutoScroller.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun end() {
        autoScroller?.stop()
        currentOnDragEnd()
    }
    Box(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(ClipSelection, shape)
            .onGloballyPositioned { coordinates[0] = it }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = ::end, onDragCancel = ::end) { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount)
                    coordinates[0]?.takeIf { it.isAttached }?.let { coords ->
                        autoScroller?.onDrag(coords.localToWindow(change.position).x) { scrolled -> currentOnDrag(scrolled) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(gripHeight)
                .background(Ink0, RoundedCornerShape(1.dp)),
        )
    }
}
