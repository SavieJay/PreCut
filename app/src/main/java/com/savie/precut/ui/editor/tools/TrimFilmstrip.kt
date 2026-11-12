package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.edit.MIN_CLIP_DURATION_MS
import com.savie.precut.ui.editor.timeline.ThumbnailTile
import com.savie.precut.ui.editor.timeline.TrimHandle
import com.savie.precut.ui.editor.timeline.TrimSide
import com.savie.precut.ui.theme.ClipBackground
import com.savie.precut.ui.theme.ClipSelection
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Radius
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The Trim panel's filmstrip (RD-T01): the whole source, the kept range ringed in pink. */
@Composable
internal fun TrimFilmstrip(
    clip: Clip,
    range: Pair<Long, Long>,
    loader: ThumbnailLoader,
    onDrag: (Pair<Long, Long>) -> Unit,
    onCommit: () -> Unit,
) {
    val density = LocalDensity.current
    val currentRange by rememberUpdatedState(range)
    // The handles sit outside the kept range, so the strip is inset by their width on each side.
    BoxWithConstraints(Modifier.fillMaxWidth().height(StripHeight + 6.dp)) {
        val inset = with(density) { HandleWidth.roundToPx() }
        val widthPx = (constraints.maxWidth - 2 * inset).coerceAtLeast(1).toFloat()
        val msPerPx = clip.sourceDurationMs.coerceAtLeast(1L) / widthPx
        val heightPx = with(density) { StripHeight.roundToPx() }
        val tilePx = heightPx * 0.75f
        val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(clip.sourceDurationMs)
        fun px(ms: Long) = ms / msPerPx
        val startPx = px(range.first)
        val endPx = px(range.second)
        // Thumbnails across the whole source.
        Box(Modifier.padding(horizontal = HandleWidth).offset(y = 3.dp).fillMaxWidth().height(StripHeight).clip(Radius.clip).background(ClipBackground)) {
            val tiles = (widthPx / tilePx).toInt() + 1
            repeat(tiles) { i ->
                val sourceMs = if (clip.type == ClipType.IMAGE) 0L else ((i + 0.5f) * tilePx * msPerPx).roundToLong().coerceAtMost(clip.sourceDurationMs)
                ThumbnailTile(
                    loader = loader,
                    key = ThumbnailKey(clip.uri, clip.type, sourceMs, heightPx, clip.sourceRotationDegrees),
                    modifier = Modifier.offset { IntOffset((i * tilePx).roundToInt(), 0) }.width(with(density) { tilePx.toDp() }).height(StripHeight),
                )
            }
            Box(Modifier.width(with(density) { startPx.toDp() }).height(StripHeight).background(Ink0.copy(alpha = 0.74f)))
            Box(Modifier.offset { IntOffset(endPx.roundToInt(), 0) }.width(with(density) { (widthPx - endPx).coerceAtLeast(0f).toDp() }).height(StripHeight).background(Ink0.copy(alpha = 0.74f)))
        }
        // The kept range: a 3 dp pink ring (RD-T01), handles outside it.
        Box(
            Modifier
                .offset { IntOffset(startPx.roundToInt() + inset, 0) }
                .size(with(density) { (endPx - startPx).coerceAtLeast(0f).toDp() }, StripHeight + 6.dp)
                .border(3.dp, ClipSelection, SelectionShape),
        )
        fun drag(isStart: Boolean, amountPx: Float) {
            val (s, e) = currentRange
            val delta = (amountPx * msPerPx).roundToLong()
            onDrag(
                if (isStart) (s + delta).coerceIn(0L, e - minLength) to e
                else s to (e + delta).coerceIn(s + minLength, clip.sourceDurationMs),
            )
        }
        TrimHandle(
            TrimSide.START,
            onDrag = { drag(isStart = true, it) },
            onDragEnd = onCommit,
            modifier = Modifier
                .offset { IntOffset(startPx.roundToInt(), 0) }
                .height(StripHeight + 6.dp),
            width = HandleWidth,
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
        )
        TrimHandle(
            TrimSide.END,
            onDrag = { drag(isStart = false, it) },
            onDragEnd = onCommit,
            modifier = Modifier
                .offset { IntOffset(endPx.roundToInt() + inset, 0) }
                .height(StripHeight + 6.dp),
            width = HandleWidth,
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
        )
    }
}

private val StripHeight = 64.dp
private val HandleWidth = 22.dp
private val SelectionShape = RoundedCornerShape(12.dp)
