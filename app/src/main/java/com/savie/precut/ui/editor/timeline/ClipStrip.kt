package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.timelineOffsetToSourceMs
import com.savie.precut.model.overlapsBeforeMs
import com.savie.precut.model.slotStartTimesMs
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Project
import com.savie.precut.model.clipStartTimesMs

/** Clips side by side, each as wide as its timeline duration. [visiblePx] is in timeline px. */
@Composable
fun ClipStrip(
    project: Project,
    selectedClipId: String?,
    multiIds: Set<String>,
    multiSelectMode: Boolean,
    missingIds: Set<String>,
    pxPerMs: Float,
    clipHeight: Dp,
    visiblePx: ClosedFloatingPointRange<Float>,
    loader: ThumbnailLoader,
    snapTargets: () -> List<Long>,
    snapThresholdMs: Long,
    onTrim: (clipId: String, trimStartMs: Long, trimEndMs: Long) -> Unit,
) {
    Row {
        // Each clip shows from its own stretch: an overlapping transition hides its head under the clip before.
        val starts = project.slotStartTimesMs()
        val overlaps = project.overlapsBeforeMs()
        project.clips.forEachIndexed { index, fullClip ->
            val hiddenMs = overlaps[index]
            val clip = if (hiddenMs > 0) fullClip.copy(trimStartMs = fullClip.timelineOffsetToSourceMs(hiddenMs)) else fullClip
            val clipStartPx = TimelineGeometry.msToPx(starts[index], pxPerMs)
            // key(): after a reorder each clip keeps its own state (drag offsets, tiles).
            key(clip.id) {
                // The selected clip's handles reach over its neighbours, so it's drawn on top.
                ClipView(
                    clip = clip,
                    clipStartMs = starts[index],
                    pxPerMs = pxPerMs,
                    height = clipHeight,
                    look = ClipLook(
                        selected = clip.id == selectedClipId,
                        multiSelected = clip.id in multiIds,
                        multiSelectMode = multiSelectMode,
                        soundOff = clip.muted || clip.volume == 0f || project.originalAudioMuted,
                        missing = clip.id in missingIds,
                    ),
                    visiblePx = (visiblePx.start - clipStartPx)..(visiblePx.endInclusive - clipStartPx),
                    loader = loader,
                    snapTargets = snapTargets,
                    snapThresholdMs = snapThresholdMs,
                    // Back to the whole clip's trim (the shown one starts later by the hidden head).
                    onTrim = { start, end -> onTrim(clip.id, start - (clip.trimStartMs - fullClip.trimStartMs), end) },
                    modifier = Modifier.zIndex(if (clip.id == selectedClipId) 1f else 0f),
                )
            }
        }
    }
}
