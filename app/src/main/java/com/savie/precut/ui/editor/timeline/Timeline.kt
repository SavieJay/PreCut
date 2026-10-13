package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Project
import com.savie.precut.model.totalDurationMs

/** Everything the timeline reports back; implemented by the editor screen with ViewModel calls. */
interface TimelineActions {
    fun onScrubStart()
    fun onSeek(positionMs: Long)
    fun onSelectClip(clipId: String?)
    fun onTrim(clipId: String, trimStartMs: Long, trimEndMs: Long)
    fun onMove(from: Int, to: Int)
    fun onSelectText(textId: String)
    fun onTextTiming(textId: String, startMs: Long, endMs: Long)
}

private val VerticalPadding = 8.dp
private val RulerHeight = 18.dp
private val LaneGap = 6.dp
private val ClipHeight = 56.dp
private val TextLaneHeight = 22.dp

/** Total height the timeline needs. */
val TimelineHeight = VerticalPadding * 2 + RulerHeight + LaneGap + ClipHeight + LaneGap + TextLaneHeight

/**
 * Horizontal timeline under a fixed centre playhead: ruler, clip strip and text lane. Scroll to
 * scrub, pinch to zoom, tap to select, long-press a clip to reorder.
 */
@Composable
fun Timeline(
    project: Project,
    selectedClipId: String?,
    selectedTextId: String?,
    positionMs: Long,
    loader: ThumbnailLoader,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val totalMs = project.totalDurationMs()
        var zoom by rememberSaveable { mutableFloatStateOf(TimelineGeometry.DEFAULT_DP_PER_SECOND) }
        val dpPerSecond = TimelineGeometry.clampZoom(zoom, totalMs, density.density)
        val pxPerMs = with(density) { dpPerSecond.dp.toPx() } / 1000f
        val widthPx = constraints.maxWidth.toFloat()
        val halfWidth = maxWidth / 2

        val scrollState = rememberScrollState()
        ScrubSync(scrollState, positionMs, pxPerMs, actions::onScrubStart, actions::onSeek)

        val reorderState = remember { mutableStateOf<ReorderState?>(null) }
        val reorder = reorderState.value
        val reorderLayout = ReorderLayout.of(
            widthPx, project.clips.size, maxTilePx = with(density) { ClipHeight.toPx() }, marginPx = with(density) { 16.dp.toPx() },
        )
        val gestures = TimelineGestureHandler(
            clips = project.clips,
            hitTest = {
                val stripTop = with(density) { (VerticalPadding + RulerHeight + LaneGap).toPx() }
                TimelineHitTest(
                    project, selectedClipId, pxPerMs, scrollState.value, widthPx,
                    handleSlopPx = with(density) { 24.dp.toPx() },
                    stripYPx = stripTop..(stripTop + with(density) { ClipHeight.toPx() }),
                )
            },
            reorderLayout = reorderLayout,
            reorder = reorderState,
            haptics = LocalHapticFeedback.current,
            actions = actions,
        )

        // Re-cull thumbnail tiles only when scrolling crosses a bucket boundary, not every pixel.
        val bucketPx = 200
        val scrollBucket by remember { derivedStateOf { scrollState.value / bucketPx } }
        val visibleStart = (scrollBucket - 1) * bucketPx - widthPx / 2
        val visibleEnd = (scrollBucket + 2) * bucketPx + widthPx / 2

        Box(
            Modifier
                .fillMaxSize()
                .pinchToZoom { factor -> zoom = TimelineGeometry.clampZoom(dpPerSecond * factor, totalMs, density.density) }
                .tapAndLongPressDrag(gestures),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState, enabled = reorder == null)
                    .padding(horizontal = halfWidth, vertical = VerticalPadding),
            ) {
                val contentWidthPx = TimelineGeometry.msToPx(totalMs, pxPerMs)
                TimelineRuler(
                    totalMs = totalMs,
                    pxPerMs = pxPerMs,
                    modifier = Modifier
                        .width(with(density) { contentWidthPx.toDp() })
                        .height(RulerHeight),
                )
                Spacer(Modifier.height(LaneGap))
                ClipStrip(
                    project = project,
                    selectedClipId = selectedClipId,
                    pxPerMs = pxPerMs,
                    clipHeight = ClipHeight,
                    visiblePx = visibleStart..visibleEnd,
                    loader = loader,
                    onTrim = actions::onTrim,
                )
                Spacer(Modifier.height(LaneGap))
                TextLane(
                    overlays = project.textOverlays,
                    selectedTextId = selectedTextId,
                    pxPerMs = pxPerMs,
                    widthPx = contentWidthPx,
                    height = TextLaneHeight,
                    onSelect = actions::onSelectText,
                    onTiming = actions::onTextTiming,
                )
            }
            Playhead(Modifier.align(Alignment.TopCenter))
            reorder?.let { state ->
                ReorderOverlay(project.clips, state, reorderLayout, loader, Modifier.matchParentSize())
            }
        }
    }
}
