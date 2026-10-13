package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.unit.Dp
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Project
import com.savie.precut.model.clipStartTimesMs

/** Clips side by side, each as wide as its timeline duration. [visiblePx] is in timeline px. */
@Composable
fun ClipStrip(
    project: Project,
    selectedClipId: String?,
    pxPerMs: Float,
    clipHeight: Dp,
    visiblePx: ClosedFloatingPointRange<Float>,
    loader: ThumbnailLoader,
    onTrim: (clipId: String, trimStartMs: Long, trimEndMs: Long) -> Unit,
) {
    Row {
        val starts = project.clipStartTimesMs()
        project.clips.forEachIndexed { index, clip ->
            val clipStartPx = TimelineGeometry.msToPx(starts[index], pxPerMs)
            // key(): after a reorder each clip keeps its own state (drag offsets, tiles).
            key(clip.id) {
                ClipView(
                    clip = clip,
                    pxPerMs = pxPerMs,
                    height = clipHeight,
                    selected = clip.id == selectedClipId,
                    visiblePx = (visiblePx.start - clipStartPx)..(visiblePx.endInclusive - clipStartPx),
                    loader = loader,
                    onTrim = { start, end -> onTrim(clip.id, start, end) },
                )
            }
        }
    }
}
