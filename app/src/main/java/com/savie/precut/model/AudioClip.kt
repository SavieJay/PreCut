package com.savie.precut.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/** Where an audio clip came from; also picks its colour on the timeline. */
@Serializable
enum class AudioKind { MUSIC, VOICEOVER, EXTRACTED }

/** Pitch-shift presets (duration unchanged). */
@Serializable
enum class VoiceEffect(val pitch: Float) {
    NONE(1f),
    CHIPMUNK(1.8f),
    HIGH(1.3f),
    DEEP(0.78f),
    MONSTER(0.6f),
}

/**
 * An audio clip on an audio lane: plays source range [trimStartMs, trimEndMs) at [speed], starting
 * at [timelineStartMs]. Fades are in timeline time; beats are in source time.
 */
@Serializable
data class AudioClip(
    val id: String,
    val uri: String,
    val sourceDurationMs: Long,
    val timelineStartMs: Long = 0,
    val trimStartMs: Long = 0,
    val trimEndMs: Long,
    val volume: Float = 1f,
    /** File name shown on the lane. */
    val displayName: String = "",
    val kind: AudioKind = AudioKind.MUSIC,
    val speed: Float = 1f,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val voiceEffect: VoiceEffect = VoiceEffect.NONE,
    val beatsMs: List<Long> = emptyList(),
)

/** Length on the timeline: `(trimEnd - trimStart) / speed`. */
fun AudioClip.timelineDurationMs(): Long = ((trimEndMs - trimStartMs) / speed.toDouble()).roundToLong()

fun AudioClip.timelineEndMs(): Long = timelineStartMs + timelineDurationMs()

/** Source time under timeline time [timelineMs] (not clamped). */
fun AudioClip.sourceAt(timelineMs: Long): Long = trimStartMs + ((timelineMs - timelineStartMs) * speed.toDouble()).roundToLong()

/** Timeline time of source time [sourceMs] (not clamped). */
fun AudioClip.timelineAt(sourceMs: Long): Long = timelineStartMs + ((sourceMs - trimStartMs) / speed.toDouble()).roundToLong()

/** The part of an audio clip that actually plays: it is cut at the video's end. */
data class AudioPlacement(
    val timelineStartMs: Long,
    val timelineDurationMs: Long,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
)

/**
 * Where the clip plays inside a video of [videoDurationMs]. Audio running past the video end is
 * cut there; audio starting at or after the end doesn't play (null).
 */
fun AudioClip.placementWithin(videoDurationMs: Long): AudioPlacement? {
    val available = videoDurationMs - timelineStartMs
    if (available <= 0L) return null
    val length = minOf(timelineDurationMs(), available)
    if (length <= 0L) return null
    val sourceEnd = if (length == timelineDurationMs()) trimEndMs else sourceAt(timelineStartMs + length).coerceAtMost(trimEndMs)
    return AudioPlacement(timelineStartMs, length, trimStartMs, sourceEnd)
}
