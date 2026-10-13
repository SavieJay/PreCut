package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Clip
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.ui.theme.ClipBackground
import com.savie.precut.ui.theme.ClipSelection
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val ClipShape = RoundedCornerShape(6.dp)

/**
 * One clip on the strip: thumbnail tiles (only those in [visiblePx], in clip-local px) plus, when
 * selected, a border and trim handles. Handle drags preview locally and call [onTrim] on release.
 */
@Composable
fun ClipView(
    clip: Clip,
    pxPerMs: Float,
    height: Dp,
    selected: Boolean,
    visiblePx: ClosedFloatingPointRange<Float>,
    loader: ThumbnailLoader,
    onTrim: (trimStartMs: Long, trimEndMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Pending handle drags, in px of timeline; applied as a trim on release.
    var startDragPx by remember(clip.id) { mutableFloatStateOf(0f) }
    var endDragPx by remember(clip.id) { mutableFloatStateOf(0f) }
    fun commitTrim(start: Long, end: Long) {
        startDragPx = 0f
        endDragPx = 0f
        onTrim(start, end)
    }
    val previewTrimStart = (clip.trimStartMs + startDragPx / pxPerMs * clip.speed).roundToLong()
        .coerceIn(0L, clip.trimEndMs)
    val previewTrimEnd = (clip.trimEndMs + endDragPx / pxPerMs * clip.speed).roundToLong()
        .coerceIn(previewTrimStart, clip.sourceDurationMs)
    val shown = clip.copy(trimStartMs = previewTrimStart, trimEndMs = previewTrimEnd)
    val widthPx = TimelineGeometry.msToPx(shown.timelineDurationMs(), pxPerMs)
    val heightPx = with(density) { height.roundToPx() }
    val tileWidthPx = heightPx * TILE_ASPECT

    Box(
        modifier
            .width(with(density) { widthPx.toDp() })
            .height(height)
            .clip(ClipShape)
            .background(ClipBackground),
    ) {
        val times = TimelineGeometry.thumbnailSourceTimesMs(shown, widthPx, tileWidthPx)
        times.forEachIndexed { i, sourceMs ->
            val left = i * tileWidthPx
            if (left + tileWidthPx >= visiblePx.start && left <= visiblePx.endInclusive) {
                ThumbnailTile(
                    loader = loader,
                    key = ThumbnailKey(clip.uri, clip.type, sourceMs, heightPx, clip.sourceRotationDegrees),
                    modifier = Modifier
                        .offset { IntOffset(left.roundToInt(), 0) }
                        .width(with(density) { tileWidthPx.toDp() })
                        .fillMaxHeight(),
                )
            }
        }
        if (selected) {
            Box(Modifier.matchParentSize().border(2.dp, ClipSelection, ClipShape))
            TrimHandle(
                onDrag = { startDragPx += it },
                onDragEnd = { commitTrim(previewTrimStart, previewTrimEnd) },
                modifier = Modifier.align(Alignment.CenterStart),
            )
            TrimHandle(
                onDrag = { endDragPx += it },
                onDragEnd = { commitTrim(previewTrimStart, previewTrimEnd) },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** Width/height ratio of a thumbnail tile. */
const val TILE_ASPECT = 0.75f
