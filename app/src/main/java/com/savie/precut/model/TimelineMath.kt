package com.savie.precut.model

import com.savie.precut.model.speed.SpeedSteps
import kotlin.math.roundToLong

/** A position inside a clip: [offsetMs] is timeline time since the clip's start. */
data class ClipPosition(val index: Int, val offsetMs: Long)

/** A curved clip's speed steps over its trimmed source, as Media3 plays them; null at constant speed. */
fun Clip.speedSteps(): SpeedSteps? =
    speedCurve?.takeIf { type == ClipType.VIDEO }?.let { SpeedSteps.of(it, (trimEndMs - trimStartMs) * 1_000) }

/** Timeline duration of a clip: `(trimEnd - trimStart) / speed`, or through its speed curve. */
fun Clip.timelineDurationMs(): Long {
    val steps = speedSteps() ?: return ((trimEndMs - trimStartMs) / speed.toDouble()).roundToLong()
    return (steps.timelineLengthUs() / 1_000.0).roundToLong()
}

/** Source time per timeline time across the whole clip (its speed, or a curve's overall speed). */
fun Clip.averageSpeed(): Float {
    if (speedSteps() == null) return speed
    val length = timelineDurationMs()
    return if (length > 0) (trimEndMs - trimStartMs).toFloat() / length else 1f
}

fun Project.totalDurationMs(): Long = clips.sumOf { it.timelineDurationMs() }

/** Timeline start time of each clip, in order. */
fun Project.clipStartTimesMs(): List<Long> =
    clips.runningFold(0L) { acc, clip -> acc + clip.timelineDurationMs() }.dropLast(1)

/**
 * The clip playing at [timelineMs]. A boundary instant belongs to the following clip, except the
 * very end of the timeline, which maps to the end of the last clip. Returns null outside
 * `[0, totalDuration]` or for an empty project.
 */
fun Project.clipAt(timelineMs: Long): ClipPosition? {
    if (clips.isEmpty() || timelineMs < 0) return null
    var start = 0L
    clips.forEachIndexed { index, clip ->
        val end = start + clip.timelineDurationMs()
        if (timelineMs < end) return ClipPosition(index, timelineMs - start)
        start = end
    }
    return if (timelineMs == start) {
        ClipPosition(clips.lastIndex, clips.last().timelineDurationMs())
    } else {
        null
    }
}

/** Maps a timeline offset within this clip to a time in the source file. */
fun Clip.timelineOffsetToSourceMs(offsetMs: Long): Long {
    val steps = speedSteps()
    val sourceOffset = if (steps == null) (offsetMs * speed.toDouble()).roundToLong() else steps.sourceAtUs(offsetMs * 1_000.0) / 1_000
    return (trimStartMs + sourceOffset).coerceIn(trimStartMs, trimEndMs)
}

/** Maps a time in the source file to a timeline offset within this clip. */
fun Clip.sourceToTimelineOffsetMs(sourceMs: Long): Long {
    val steps = speedSteps()
    val offset = if (steps == null) {
        ((sourceMs - trimStartMs) / speed.toDouble()).roundToLong()
    } else {
        (steps.timelineAtUs((sourceMs - trimStartMs) * 1_000) / 1_000.0).roundToLong()
    }
    return offset.coerceIn(0L, timelineDurationMs())
}

/**
 * Where new clips go for a playhead at [timelineMs]: the clip boundary nearest to it (before the
 * clip under the playhead in its first half, after it in its second half). The end when the
 * playhead is past the last clip or the project is empty.
 */
fun Project.insertIndexAt(timelineMs: Long): Int {
    val position = clipAt(timelineMs) ?: return if (timelineMs <= 0L) 0 else clips.size
    val length = clips[position.index].timelineDurationMs()
    return if (position.offsetMs * 2 < length) position.index else position.index + 1
}
