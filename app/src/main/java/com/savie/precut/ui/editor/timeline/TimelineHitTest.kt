package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.slotStartTimesMs
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
    /** Vertical band (px from the viewport top) occupied by the clip strip. */
    private val stripYPx: ClosedFloatingPointRange<Float>,
) {
    fun isInClipStrip(y: Float): Boolean = y in stripYPx

    private fun toTimelinePx(x: Float) = x + scrollPx - viewportWidthPx / 2
    private fun toViewportX(timelinePx: Float) = timelinePx - scrollPx + viewportWidthPx / 2

    /** Index of the clip under ([x], [y]), or null over empty space or outside the clip strip. */
    fun clipIndexAt(x: Float, y: Float): Int? {
        if (!isInClipStrip(y)) return null
        val ms = TimelineGeometry.pxToMs(toTimelinePx(x), pxPerMs)
        val total = project.totalDurationMs()
        return if (ms in 0 until total) project.clipAt(ms)?.index else null
    }

    /** True near the selected clip's edges, where a press belongs to a trim handle. */
    fun isOnTrimHandle(x: Float): Boolean {
        val index = project.clips.indexOfFirst { it.id == selectedClipId }
        if (index < 0) return false
        // The clip shows from its own stretch (an overlap hides its head under the clip before).
        val startX = toViewportX(TimelineGeometry.msToPx(project.slotStartTimesMs()[index], pxPerMs))
        val endX = toViewportX(TimelineGeometry.msToPx(project.clipStartTimesMs()[index] + project.clips[index].timelineDurationMs(), pxPerMs))
        return abs(x - startX) < handleSlopPx || abs(x - endX) < handleSlopPx
    }
}
