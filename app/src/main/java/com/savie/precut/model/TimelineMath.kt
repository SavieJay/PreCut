package com.savie.precut.model

import com.savie.precut.model.motion.MotionMath
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

/** How much clip [incoming] starts before [outgoing] ends: an overlapping transition between them, else 0. */
fun overlapMs(outgoing: Clip, incoming: Clip): Long {
    val transition = outgoing.transitionOut?.takeIf { it.type.overlapping } ?: return 0L
    return MotionMath.fittedOverlapMs(transition, outgoing.timelineDurationMs(), incoming.timelineDurationMs())
}

/** Each clip's overlap with the one before it (0 for the first). */
fun Project.overlapsBeforeMs(): List<Long> = clips.indices.map { i -> if (i == 0) 0L else overlapMs(clips[i - 1], clips[i]) }

fun Project.totalDurationMs(): Long {
    val starts = clipStartTimesMs()
    return if (clips.isEmpty()) 0L else starts.last() + clips.last().timelineDurationMs()
}

/** Timeline start time of each clip, in order (an overlapping transition starts a clip early). */
fun Project.clipStartTimesMs(): List<Long> {
    val overlaps = overlapsBeforeMs()
    var end = 0L
    return clips.mapIndexed { i, clip ->
        val start = end - overlaps[i]
        end = start + clip.timelineDurationMs()
        start
    }
}

/**
 * Where each clip's own stretch begins: its start, or the previous clip's end when it overlaps.
 * The timeline strip shows each clip from here, and junctions sit here.
 */
fun Project.slotStartTimesMs(): List<Long> = clipStartTimesMs().zip(overlapsBeforeMs()) { start, overlap -> start + overlap }

/**
 * The clip playing at [timelineMs]. A boundary instant belongs to the following clip, except the
 * very end of the timeline, which maps to the end of the last clip. Returns null outside
 * `[0, totalDuration]` or for an empty project.
 */
fun Project.clipAt(timelineMs: Long): ClipPosition? {
    if (clips.isEmpty() || timelineMs < 0) return null
    // Inside an overlap, the clip that's ending still owns the time.
    val starts = clipStartTimesMs()
    var end = 0L
    clips.forEachIndexed { index, clip ->
        end = starts[index] + clip.timelineDurationMs()
        if (timelineMs < end) return ClipPosition(index, timelineMs - starts[index])
    }
    return if (timelineMs == end) {
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
