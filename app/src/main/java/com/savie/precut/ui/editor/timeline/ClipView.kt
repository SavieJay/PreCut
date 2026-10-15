package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Clip
import com.savie.precut.model.averageSpeed
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.ClipBackground
import com.savie.precut.ui.theme.ClipSelection
import com.savie.precut.ui.theme.ErrorRed
import com.savie.precut.ui.theme.Ink0
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val ClipShape = RoundedCornerShape(6.dp)

/** How a clip is drawn on the strip. */
data class ClipLook(
    /** Single selection: border, trim handles and duration badge. */
    val selected: Boolean,
    /** Picked in multi-select: Accent outline and check badge. */
    val multiSelected: Boolean,
    /** Multi-select is on: unpicked clips show an empty check circle. */
    val multiSelectMode: Boolean = false,
    /** Shows the muted badge (clip muted, at 0%, or all original audio muted). */
    val soundOff: Boolean,
    /** The media can't be opened: tinted red with a warning mark. */
    val missing: Boolean,
)

/**
 * One clip on the strip: thumbnail tiles (only those in [visiblePx], in clip-local px), badges and,
 * when selected, trim handles. Handle drags snap to [snapTargets] and preview locally; the start
 * handle follows the finger while the clip keeps its slot, so neighbours don't move mid-drag.
 * The trim is applied on release via [onTrim].
 */
@Composable
fun ClipView(
    clip: Clip,
    clipStartMs: Long,
    pxPerMs: Float,
    height: Dp,
    look: ClipLook,
    visiblePx: ClosedFloatingPointRange<Float>,
    loader: ThumbnailLoader,
    snapTargets: () -> List<Long>,
    snapThresholdMs: Long,
    onTrim: (trimStartMs: Long, trimEndMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    // Pending handle drags, in px of timeline; applied as a trim on release.
    var startDragPx by remember(clip.id) { mutableFloatStateOf(0f) }
    var endDragPx by remember(clip.id) { mutableFloatStateOf(0f) }
    val dragging = startDragPx != 0f || endDragPx != 0f
    val trim = TimelineGeometry.trimDrag(
        clip, clipStartMs, (startDragPx / pxPerMs).roundToLong(), (endDragPx / pxPerMs).roundToLong(),
        if (dragging) snapTargets() else emptyList(), snapThresholdMs,
    )
    LaunchedEffect(trim.snapped) { if (trim.snapped) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    fun commitTrim() {
        startDragPx = 0f
        endDragPx = 0f
        onTrim(trim.trimStartMs, trim.trimEndMs)
    }
    val shown = clip.copy(trimStartMs = trim.trimStartMs, trimEndMs = trim.trimEndMs)
    val widthPx = TimelineGeometry.msToPx(shown.timelineDurationMs(), pxPerMs)
    // The start handle moves the clip's left edge; its slot (and the following clips) stay put.
    val leftShiftPx = TimelineGeometry.msToPx(((trim.trimStartMs - clip.trimStartMs) / clip.averageSpeed()).roundToLong(), pxPerMs)
    val slotWidthPx = (widthPx + leftShiftPx).coerceAtLeast(0f)
    val heightPx = with(density) { height.roundToPx() }
    val tileWidthPx = heightPx * TILE_ASPECT

    Box(modifier.width(with(density) { slotWidthPx.toDp() }).height(height)) {
        Box(
            Modifier
                .offset { IntOffset(leftShiftPx.roundToInt(), 0) }
                .width(with(density) { widthPx.toDp() })
                .fillMaxHeight()
                .clip(ClipShape)
                .background(ClipBackground),
        ) {
            val times = TimelineGeometry.thumbnailSourceTimesMs(shown, widthPx, tileWidthPx)
            times.forEachIndexed { i, sourceMs ->
                val left = i * tileWidthPx
                if (left + tileWidthPx >= visiblePx.start - leftShiftPx && left <= visiblePx.endInclusive - leftShiftPx) {
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
            if (look.missing) MissingMark()
            ClipBadges(
                speed = clip.speed,
                curved = clip.speedCurve != null,
                durationMs = if (look.selected) shown.timelineDurationMs() else null,
                soundOff = look.soundOff,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            )
            if (look.multiSelected) Box(Modifier.matchParentSize().border(2.dp, Accent, ClipShape))
            if (look.multiSelectMode) CheckBadge(look.multiSelected, Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
        if (look.selected) {
            // A 2 dp ring 2 dp outside the clip, and 14 × 60 handles outside its ends (E-04).
            val ring = with(density) { 2.dp.roundToPx() }
            val handle = with(density) { 14.dp.roundToPx() }
            Box(
                Modifier
                    .offset { IntOffset(leftShiftPx.roundToInt() - ring, -ring) }
                    .requiredSize(with(density) { widthPx.toDp() } + 4.dp, height + 4.dp)
                    .border(2.dp, ClipSelection, RoundedCornerShape(7.dp)),
            )
            TrimHandle(
                TrimSide.START,
                onDrag = { startDragPx += it }, onDragEnd = ::commitTrim,
                modifier = Modifier
                    .offset { IntOffset(leftShiftPx.roundToInt() - handle, -ring) }
                    .requiredHeight(height + 4.dp),
                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp),
            )
            TrimHandle(
                TrimSide.END,
                onDrag = { endDragPx += it }, onDragEnd = ::commitTrim,
                modifier = Modifier
                    .offset { IntOffset((leftShiftPx + widthPx).roundToInt(), -ring) }
                    .requiredHeight(height + 4.dp),
                shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp),
            )
        }
    }
}

/** Missing media: the clip tinted ErrorRed with a round "!" (E-06). */
@Composable
private fun BoxScope.MissingMark() {
    Box(Modifier.matchParentSize().background(ErrorRed.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).background(ErrorRed, CircleShape), contentAlignment = Alignment.Center) {
            Text("!", style = MaterialTheme.typography.titleSmall, color = Ink0)
        }
    }
}

/** Multi-select check: an Accent disc with a check when picked, an empty white ring otherwise (E-07). */
@Composable
private fun CheckBadge(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(18.dp)
            .background(if (checked) Accent else Ink0.copy(alpha = 0.6f), CircleShape)
            .then(if (checked) Modifier else Modifier.border(1.5.dp, Color.White, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

/** Width/height ratio of a thumbnail tile. */
const val TILE_ASPECT = 0.75f
