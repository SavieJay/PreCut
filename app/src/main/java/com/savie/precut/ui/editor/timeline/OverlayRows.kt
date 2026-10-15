package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.ClipType
import com.savie.precut.model.OverlayClip
import com.savie.precut.model.averageSpeed
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.OverlayBar
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Callbacks from the overlay rows. */
interface OverlayRowActions {
    fun onSelectOverlay(overlayId: String)
    fun onOverlayTiming(overlayId: String, timelineStartMs: Long, trimStartMs: Long, trimEndMs: Long)
}

/**
 * Overlay rows above the main strip (P-01), the top row first (it also draws on top). Each overlay
 * is a bar of its thumbnails with a PiP badge; drag to move, handles to trim. Drawn only up to the
 * video's end, where overlays stop playing.
 */
@Composable
fun OverlayRows(
    lanes: List<List<OverlayClip>>,
    selectedId: String?,
    videoDurationMs: Long,
    pxPerMs: Float,
    rowHeight: Dp,
    rowGap: Dp,
    loader: ThumbnailLoader,
    snap: (ownerId: String, startMs: Long, endMs: Long, moveMs: Long) -> Long,
    actions: OverlayRowActions,
) {
    val width = with(LocalDensity.current) { (videoDurationMs * pxPerMs).toDp() }
    Column {
        lanes.asReversed().forEach { lane ->
            Box(Modifier.width(width).height(rowHeight)) {
                lane.forEach { overlay ->
                    key(overlay.id) { OverlayBarView(overlay, overlay.id == selectedId, videoDurationMs, pxPerMs, rowHeight, loader, snap, actions) }
                }
            }
            Spacer(Modifier.height(rowGap))
        }
    }
}

@Composable
private fun OverlayBarView(
    overlay: OverlayClip,
    selected: Boolean,
    videoDurationMs: Long,
    pxPerMs: Float,
    rowHeight: Dp,
    loader: ThumbnailLoader,
    snap: (ownerId: String, startMs: Long, endMs: Long, moveMs: Long) -> Long,
    actions: OverlayRowActions,
) {
    val clip = overlay.clip
    val speed = clip.averageSpeed()
    val full = TimelineGeometry.AudioTiming(overlay.timelineStartMs, clip.trimStartMs, clip.trimEndMs)
    fun timing(drag: BarDrag) = TimelineGeometry.dragAudio(full, clip.sourceDurationMs, speed, drag.moveMs, drag.startMs, drag.endMs)
    fun range(drag: BarDrag): Pair<Long, Long> = timing(drag).let {
        val end = it.timelineStartMs + ((it.trimEndMs - it.trimStartMs) / speed).roundToLong()
        it.timelineStartMs to minOf(end, videoDurationMs).coerceAtLeast(it.timelineStartMs + 1)
    }
    val density = LocalDensity.current
    val heightPx = with(density) { rowHeight.roundToPx() }
    TimelineBar(
        id = overlay.id,
        label = formatSeconds(minOf(overlay.timelineEndMs, videoDurationMs) - overlay.timelineStartMs),
        color = OverlayBar,
        labelColor = Ink0,
        selected = selected,
        glyph = R.drawable.ic_overlay,
        pxPerMs = pxPerMs,
        rangeFor = ::range,
        onSelect = { actions.onSelectOverlay(overlay.id) },
        onCommit = { drag -> timing(drag).let { actions.onOverlayTiming(overlay.id, it.timelineStartMs, it.trimStartMs, it.trimEndMs) } },
        snapMove = { move -> snap(overlay.id, overlay.timelineStartMs, minOf(overlay.timelineEndMs, videoDurationMs), move) },
        decoration = { startMs, endMs ->
            // Thumbnails under the label, faded so it stays readable.
            val tilePx = heightPx * TILE_ASPECT
            val widthPx = TimelineGeometry.msToPx(endMs - startMs, pxPerMs)
            val tiles = (widthPx / tilePx).toInt().coerceAtLeast(0) + 1
            for (i in 0 until tiles) {
                val sourceMs = if (clip.type == ClipType.IMAGE) 0L else clip.trimStartMs + ((i * tilePx / pxPerMs) * speed).roundToLong()
                ThumbnailTile(
                    loader = loader,
                    key = ThumbnailKey(clip.uri, clip.type, sourceMs.coerceAtMost(clip.trimEndMs), heightPx, clip.sourceRotationDegrees),
                    modifier = Modifier
                        .offset { IntOffset((i * tilePx).roundToInt(), 0) }
                        .width(with(density) { tilePx.toDp() })
                        .fillMaxHeight()
                        .alpha(0.55f),
                )
            }
        },
    )
}

/** Height of the overlay rows above the main strip. */
internal fun overlayRowsHeight(rows: Int): Dp = (OverlayRowHeight + StripGap) * rows

internal val OverlayRowHeight = 40.dp
