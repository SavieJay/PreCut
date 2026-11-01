package com.savie.precut.model.edit

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.frame.contentAspect
import com.savie.precut.model.frame.PathPoint
import com.savie.precut.model.frame.PicturePoint
import com.savie.precut.model.frame.Reframe
import com.savie.precut.model.frame.SubjectSample
import com.savie.precut.model.keyframe.TextKeyframe
import com.savie.precut.model.sourceToTimelineOffsetMs
import com.savie.precut.model.timelineDurationMs

/**
 * Follow person (Phase 26A, CapCut's motion tracking, free here): a text or sticker keeps its place
 * next to the person in the main clip under it. The person's place in the frame comes from the
 * tracker's [samples] through the clip's own framing at each moment (fit or fill, scale, offset and
 * keyframes, mirror, turn and crop); the text keeps the offset from the person it has at its start.
 */
fun TextOverlay.followingPerson(clip: Clip, clipStartMs: Long, samples: List<SubjectSample>, frameAspect: Float): TextOverlay? {
    val points = samples.mapNotNull { sample ->
        val point = sample.point ?: return@mapNotNull null
        val offset = if (clip.type == ClipType.IMAGE) 0L else clip.sourceToTimelineOffsetMs(sample.sourceMs)
        val timelineMs = clipStartMs + offset
        if (timelineMs < startMs || timelineMs > endMs) return@mapNotNull null
        val (x, y) = clip.atOffset(offset.coerceIn(0L, clip.timelineDurationMs())).framePoint(point, frameAspect)
        PathPoint(timelineMs - startMs, x, y)
    }.sortedBy { it.timeMs }.distinctBy { it.timeMs }
    if (points.isEmpty()) return null
    val smoothed = Reframe.simplify(smooth(points))
    // The text's offset from the person when the path starts stays the same throughout.
    val first = snapshotAt(smoothed.first().timeMs)
    val dx = first.xNorm - smoothed.first().offsetX
    val dy = first.yNorm - smoothed.first().offsetY
    val keyframes = smoothed.map { point ->
        snapshotAt(point.timeMs).copy(atMs = point.timeMs, xNorm = (point.offsetX + dx).coerceIn(0f, 1f), yNorm = (point.offsetY + dy).coerceIn(0f, 1f))
    }
    return copy(keyframes = keyframes.distinctBy(TextKeyframe::atMs))
}

/** Where [point] of the upright picture shows in the frame (0..1, y down), as this clip is framed. */
internal fun Clip.framePoint(point: PicturePoint, frameAspect: Float): Pair<Float, Float> {
    val content = Reframe.toContent(point, mirrored, rotationDegrees, crop)
    val aspect = contentAspect()
    val (width, height) = when (fit) {
        FrameFit.FILL -> Reframe.filledSize(aspect, frameAspect, transform.scale)
        else -> if (aspect > frameAspect) {
            transform.scale to frameAspect / aspect * transform.scale
        } else {
            aspect / frameAspect * transform.scale to transform.scale
        }
    }
    return (0.5f + transform.offsetX + (content.x - 0.5f) * width) to (0.5f + transform.offsetY + (content.y - 0.5f) * height)
}

/**
 * A centred moving average over three samples, so a jittery track doesn't shake the text; the
 * window narrows symmetrically at the ends, so the first and last places stay where they are.
 */
private fun smooth(points: List<PathPoint>): List<PathPoint> = points.mapIndexed { i, point ->
    val radius = minOf(1, i, points.lastIndex - i)
    val window = points.subList(i - radius, i + radius + 1)
    point.copy(offsetX = window.map { it.offsetX }.average().toFloat(), offsetY = window.map { it.offsetY }.average().toFloat())
}
