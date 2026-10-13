package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** How a clip is drawn on the strip. */
data class ClipLook(
    /** Single selection: border, trim handles and duration badge. */
    val selected: Boolean,
    /** Picked in multi-select: border only. */
    val multiSelected: Boolean,
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
    val leftShiftPx = TimelineGeometry.msToPx(((trim.trimStartMs - clip.trimStartMs) / clip.speed).roundToLong(), pxPerMs)
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
            if (look.missing) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("!", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
            ClipBadges(
                speed = clip.speed,
                durationMs = if (look.selected) shown.timelineDurationMs() else null,
                soundOff = look.soundOff,
                modifier = Modifier.align(Alignment.TopStart),
            )
            if (look.selected || look.multiSelected) {
                Box(Modifier.matchParentSize().border(2.dp, ClipSelection, ClipShape))
            }
            if (look.selected) {
                TrimHandle(onDrag = { startDragPx += it }, onDragEnd = ::commitTrim, modifier = Modifier.align(Alignment.CenterStart))
                TrimHandle(onDrag = { endDragPx += it }, onDragEnd = ::commitTrim, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

/** Width/height ratio of a thumbnail tile. */
const val TILE_ASPECT = 0.75f
