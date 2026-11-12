package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.savie.precut.R
import com.savie.precut.ui.theme.ClipSelection
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.Ink0

/** Which end of an item a [TrimHandle] trims. */
enum class TrimSide { START, END }

/** How far a handle's grab area reaches past its drawn edges, towards and away from the item. */
private val GrabInward = 14.dp
private val GrabOutward = 16.dp

/**
 * Grip on a selected item's edge; reports horizontal drag deltas in px, including the
 * timeline's auto-scroll when held near the screen edge. Its grab area is wider than the grip,
 * reaching [GrabInward] into the item and [GrabOutward] away from it, so the edge is easy to catch.
 */
@Composable
fun TrimHandle(
    side: TrimSide,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    /** 14 dp for clips, 10 dp for bars. */
    width: Dp = 14.dp,
    gripHeight: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(4.dp),
    color: Color = ClipSelection,
    /** A dark chevron pointing outward (clips, RD-E04) instead of the 2 dp grip line (bars). */
    chevron: Boolean = false,
    /** How far the grab area reaches away from the item, past the drawn handle. */
    grabOutward: Dp = GrabOutward,
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val autoScroller = LocalEdgeAutoScroller.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val density = LocalDensity.current
    val inward = with(density) { GrabInward.roundToPx() }
    val outward = with(density) { grabOutward.roundToPx() }
    fun end() {
        autoScroller?.stop()
        currentOnDragEnd()
    }
    Box(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(color, shape)
            .onGloballyPositioned { coordinates[0] = it }
            .expandedHorizontalDrag(
                expandLeftPx = if (side == TrimSide.START) outward else inward,
                expandRightPx = if (side == TrimSide.START) inward else outward,
                onEnd = ::end,
            ) { change, dragAmount ->
                change.consume()
                currentOnDrag(dragAmount)
                coordinates[0]?.takeIf { it.isAttached }?.let { coords ->
                    autoScroller?.onDrag(coords.localToWindow(change.position).x) { scrolled -> currentOnDrag(scrolled) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (chevron) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = OnAccent,
                modifier = Modifier.size(20.dp).graphicsLayer { if (side == TrimSide.START) scaleX = -1f },
            )
        } else {
            Box(
                Modifier
                    .width(2.dp)
                    .height(gripHeight)
                    .background(Ink0, RoundedCornerShape(1.dp)),
            )
        }
    }
}
