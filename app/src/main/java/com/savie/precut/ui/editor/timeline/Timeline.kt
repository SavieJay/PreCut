package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.media.WaveformLoader
import com.savie.precut.model.Project
import com.savie.precut.model.totalDurationMs

/** Everything the timeline reports back; implemented by the editor screen with ViewModel calls. */
interface TimelineActions : AudioRowActions, LookLayerActions {
    fun onScrubStart()
    fun onScrubEnd()
    fun onSeek(positionMs: Long)
    fun onSelectClip(clipId: String?)
    fun onTrim(clipId: String, trimStartMs: Long, trimEndMs: Long)
    fun onMove(from: Int, to: Int)
    fun onSelectText(textId: String)
    fun onTextTiming(textId: String, startMs: Long, endMs: Long)
    fun onToggleOriginalAudio()
}

/**
 * Horizontal timeline under a fixed centre playhead: ruler, clips, text and music lanes. Scroll to
 * scrub, pinch to zoom, tap to select, long-press a clip to reorder.
 */
@Composable
fun Timeline(
    project: Project,
    selection: TimelineSelection,
    /** Read lazily (in effects and lambdas), so the timeline doesn't recompose as time moves. */
    positionMs: () -> Long,
    loader: ThumbnailLoader,
    waveforms: WaveformLoader,
    actions: TimelineActions,
    modifier: Modifier = Modifier,
    /** Only the ruler and main strip (a tall panel is open). */
    compact: Boolean = false,
    /** The pinned "+" add-media button, shown at the right of the main strip. */
    addButton: @Composable () -> Unit = {},
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
        ScrubSync(scrollState, positionMs, pxPerMs, actions::onScrubStart, actions::onScrubEnd, actions::onSeek)
        val coroutineScope = rememberCoroutineScope()
        val autoScroller = remember(scrollState) {
            with(density) { EdgeAutoScroller(scrollState, coroutineScope, edgePx = 48.dp.toPx(), maxSpeedPx = 14.dp.toPx()) }
        }
        autoScroller.onScrollTo = { px ->
            actions.onScrubStart()
            actions.onSeek(TimelineGeometry.pxToMs(px.toFloat(), pxPerMs))
        }
        autoScroller.onScrollEnd = actions::onScrubEnd

        val reorderState = remember { mutableStateOf<ReorderState?>(null) }
        val reorder = reorderState.value
        val reorderLayout = ReorderLayout.of(
            widthPx, project.clips.size, maxTilePx = with(density) { ReorderTileSize.toPx() }, marginPx = with(density) { 16.dp.toPx() },
        )
        val gestures = TimelineGestureHandler(
            clips = project.clips,
            hitTest = {
                val stripTop = with(density) { StripTop.toPx() }
                TimelineHitTest(
                    project, selection.clipId, pxPerMs, scrollState.value, widthPx,
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
                .onGloballyPositioned {
                    autoScroller.viewportLeftPx = it.positionInWindow().x
                    autoScroller.viewportWidthPx = it.size.width.toFloat()
                }
                .pinchToZoom { factor -> zoom = TimelineGeometry.clampZoom(dpPerSecond * factor, totalMs, density.density) }
                .tapAndLongPressDrag(gestures),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState, enabled = reorder == null)
                    .padding(start = halfWidth, end = halfWidth, top = TopPadding, bottom = BottomPadding),
            ) {
                CompositionLocalProvider(LocalEdgeAutoScroller provides autoScroller) {
                    CompositionLocalProvider(LocalTimelineViewport provides TimelineViewport(scrollState, widthPx / 2)) {
                        TimelineLanes(project, selection, pxPerMs, visibleStart..visibleEnd, loader, waveforms, positionMs, actions, compact)
                    }
                }
            }
            // Mute and "+" stay pinned at the strip's ends; clips slide under 64 dp fades.
            PinnedZone(start = true, Modifier.align(Alignment.TopStart).padding(top = StripTop)) {
                MuteOriginalButton(muted = project.originalAudioMuted, onClick = actions::onToggleOriginalAudio)
            }
            PinnedZone(start = false, Modifier.align(Alignment.TopEnd).padding(top = StripTop)) { addButton() }
            Playhead(Modifier.align(Alignment.TopCenter))
            reorder?.let { state ->
                ReorderOverlay(project.clips, state, reorderLayout, loader, Modifier.matchParentSize())
            }
        }
    }
}
