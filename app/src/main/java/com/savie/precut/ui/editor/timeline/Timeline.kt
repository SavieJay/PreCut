package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.totalDurationMs

/** Everything the timeline reports back; implemented by the editor screen with ViewModel calls. */
interface TimelineActions {
    fun onScrubStart()
    fun onSeek(positionMs: Long)
    fun onSelectClip(clipId: String?)
    fun onTrim(clipId: String, trimStartMs: Long, trimEndMs: Long)
    fun onMove(from: Int, to: Int)
}

private val ClipHeight = 56.dp
private val RulerHeight = 18.dp

/**
 * Horizontal timeline under a fixed centre playhead: ruler + clip strip. Scroll to scrub, pinch to
 * zoom, tap to select, long-press to reorder.
 */
@Composable
fun Timeline(
    project: Project,
    selectedClipId: String?,
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
                TimelineHitTest(project, selectedClipId, pxPerMs, scrollState.value, widthPx, with(density) { 24.dp.toPx() })
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
                    .padding(horizontal = halfWidth, vertical = 8.dp),
            ) {
                TimelineRuler(
                    totalMs = totalMs,
                    pxPerMs = pxPerMs,
                    modifier = Modifier
                        .width(with(density) { TimelineGeometry.msToPx(totalMs, pxPerMs).toDp() })
                        .height(RulerHeight),
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    val starts = project.clipStartTimesMs()
                    project.clips.forEachIndexed { index, clip ->
                        val clipStartPx = TimelineGeometry.msToPx(starts[index], pxPerMs)
                        ClipView(
                            clip = clip,
                            pxPerMs = pxPerMs,
                            height = ClipHeight,
                            selected = clip.id == selectedClipId,
                            visiblePx = (visibleStart - clipStartPx)..(visibleEnd - clipStartPx),
                            loader = loader,
                            onTrim = { start, end -> actions.onTrim(clip.id, start, end) },
                        )
                    }
                }
            }
            Playhead(Modifier.align(Alignment.TopCenter))
            reorder?.let { state ->
                ReorderOverlay(project.clips, state, reorderLayout, loader, Modifier.matchParentSize())
            }
        }
    }
}
