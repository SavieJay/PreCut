package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Clip
import com.savie.precut.ui.theme.ClipSelection
import com.savie.precut.ui.theme.Ink0
import kotlin.math.roundToInt

/** An in-progress long-press reorder: which clip is lifted and where the finger is (px). */
data class ReorderState(val fromIndex: Int, val fingerX: Float)

/** Equal-size tiles centred in the timeline while reordering. */
data class ReorderLayout(val tilePx: Float, val startPx: Float) {
    fun targetIndex(fingerX: Float, count: Int): Int =
        TimelineGeometry.reorderTargetIndex(fingerX - startPx, tilePx, count)

    companion object {
        fun of(widthPx: Float, count: Int, maxTilePx: Float, marginPx: Float): ReorderLayout {
            val tile = if (count == 0) maxTilePx else minOf(maxTilePx, (widthPx - 2 * marginPx) / count)
            return ReorderLayout(tile, (widthPx - tile * count) / 2f)
        }
    }
}

private val TileShape = RoundedCornerShape(6.dp)

/** Reorder tiles are equal squares (E-05). */
internal val ReorderTileSize = 48.dp

@Composable
fun ReorderOverlay(
    clips: List<Clip>,
    state: ReorderState,
    layout: ReorderLayout,
    loader: ThumbnailLoader,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val tileSize = with(density) { layout.tilePx.toDp() }
    val tileHeightPx = with(density) { tileSize.roundToPx() }
    val target = layout.targetIndex(state.fingerX, clips.size)
    // Show the order the user would get if they let go now; the lifted clip leaves a gap.
    val order = clips.indices.toMutableList().apply { add(target, removeAt(state.fromIndex)) }

    Box(modifier.background(Ink0.copy(alpha = 0.94f)), contentAlignment = Alignment.CenterStart) {
        order.forEachIndexed { slot, clipIndex ->
            val slotX = layout.startPx + slot * layout.tilePx
            if (clipIndex == state.fromIndex) {
                // Where the lifted clip will land: a 3 dp white bar in its slot.
                Box(
                    Modifier
                        .offset { IntOffset((slotX + layout.tilePx / 2).roundToInt() - 3.dp.roundToPx() / 2, 0) }
                        .width(3.dp)
                        .height(tileSize)
                        .background(ClipSelection, RoundedCornerShape(2.dp)),
                )
            } else {
                ReorderTile(
                    clip = clips[clipIndex],
                    loader = loader,
                    heightPx = tileHeightPx,
                    modifier = Modifier
                        .offset { IntOffset(slotX.roundToInt(), 0) }
                        .size(tileSize)
                        .padding(2.dp),
                )
            }
        }
        // The lifted tile follows the finger, scaled ×1.1 with a shadow.
        ReorderTile(
            clip = clips[state.fromIndex],
            loader = loader,
            heightPx = tileHeightPx,
            modifier = Modifier
                .offset { IntOffset((state.fingerX - layout.tilePx / 2).roundToInt(), 0) }
                .size(tileSize)
                .scale(1.1f)
                .shadow(8.dp, TileShape),
        )
    }
}

@Composable
private fun ReorderTile(clip: Clip, loader: ThumbnailLoader, heightPx: Int, modifier: Modifier) {
    ThumbnailTile(
        loader = loader,
        key = ThumbnailKey(clip.uri, clip.type, clip.trimStartMs, heightPx, clip.sourceRotationDegrees),
        modifier = modifier.clip(TileShape),
    )
}
