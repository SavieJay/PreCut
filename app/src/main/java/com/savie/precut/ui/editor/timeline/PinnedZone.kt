package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Ink0

/**
 * A zone pinned at one end of the main strip, fading to Base 0 so clips slide underneath: at the
 * end, 72 dp holding the "+" 12 dp from the edge (RD-E03 `.pinr`); at the start, the gutter's width.
 */
@Composable
fun PinnedZone(start: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val fade = if (start) listOf(Ink0, Ink0, Color.Transparent) else listOf(Color.Transparent, Ink0, Ink0, Ink0)
    Box(
        modifier
            .width(if (start) GutterWidth else 72.dp)
            .height(ClipHeight)
            .background(Brush.horizontalGradient(fade))
            .padding(start = if (start) 8.dp else 0.dp, end = if (start) 0.dp else 12.dp),
        contentAlignment = if (start) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        content()
    }
}

/** The timeline's scroll and the width of the empty lead-in before time 0 (half the viewport). */
class TimelineViewport(val scroll: ScrollState, val leadInPx: Float) {
    /** The viewport's left edge in lane coordinates (lane x = 0 is time 0). */
    fun visibleLeftPx(): Float = scroll.value - leadInPx
}

/** Lets bars keep their labels at the visible left edge without recomposing as the timeline scrolls. */
val LocalTimelineViewport = compositionLocalOf<TimelineViewport?> { null }
