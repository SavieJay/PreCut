package com.savie.precut.model

import kotlin.math.roundToLong

/** A position inside a clip: [offsetMs] is timeline time since the clip's start. */
data class ClipPosition(val index: Int, val offsetMs: Long)

/** Timeline duration of a clip: `(trimEnd - trimStart) / speed`. */
fun Clip.timelineDurationMs(): Long = ((trimEndMs - trimStartMs) / speed.toDouble()).roundToLong()

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
fun Clip.timelineOffsetToSourceMs(offsetMs: Long): Long =
    (trimStartMs + (offsetMs * speed.toDouble()).roundToLong()).coerceIn(trimStartMs, trimEndMs)

/** Maps a time in the source file to a timeline offset within this clip. */
fun Clip.sourceToTimelineOffsetMs(sourceMs: Long): Long =
    ((sourceMs - trimStartMs) / speed.toDouble()).roundToLong().coerceIn(0L, timelineDurationMs())
