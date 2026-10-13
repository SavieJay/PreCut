package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.Project
import com.savie.precut.model.clipAt
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.timelineDurationMs
import kotlin.math.abs

/**
 * Maps a touch x (px from the timeline viewport's left edge) to clips. Time 0 sits under the
 * centre playhead when [scrollPx] is 0, so viewport x → timeline px is `x + scrollPx - viewportWidth/2`.
 */
class TimelineHitTest(
    private val project: Project,
    private val selectedClipId: String?,
    private val pxPerMs: Float,
    private val scrollPx: Int,
    private val viewportWidthPx: Float,
    private val handleSlopPx: Float,
) {
    private fun toTimelinePx(x: Float) = x + scrollPx - viewportWidthPx / 2
    private fun toViewportX(timelinePx: Float) = timelinePx - scrollPx + viewportWidthPx / 2

    /** Index of the clip under [x], or null over empty space. */
    fun clipIndexAt(x: Float): Int? {
        val ms = TimelineGeometry.pxToMs(toTimelinePx(x), pxPerMs)
        val total = project.clips.sumOf { it.timelineDurationMs() }
        return if (ms in 0 until total) project.clipAt(ms)?.index else null
    }

    /** True near the selected clip's edges, where a press belongs to a trim handle. */
    fun isOnTrimHandle(x: Float): Boolean {
        val index = project.clips.indexOfFirst { it.id == selectedClipId }
        if (index < 0) return false
        val startX = toViewportX(TimelineGeometry.msToPx(project.clipStartTimesMs()[index], pxPerMs))
        val endX = startX + TimelineGeometry.msToPx(project.clips[index].timelineDurationMs(), pxPerMs)
        return abs(x - startX) < handleSlopPx || abs(x - endX) < handleSlopPx
    }
}
