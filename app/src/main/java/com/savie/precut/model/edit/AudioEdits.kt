package com.savie.precut.model.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.MAX_AUDIO_SPEED
import com.savie.precut.model.MAX_VOLUME
import com.savie.precut.model.MIN_AUDIO_SPEED
import com.savie.precut.model.Project
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.sourceAt
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineEndMs
import com.savie.precut.model.totalDurationMs
import kotlin.math.abs

/** Beats this close (source time) to a tapped time are toggled off rather than duplicated. */
const val BEAT_TOLERANCE_MS = 150L

/** Applies an audio-related action; the same instance when nothing changed. */
internal fun Project.applyAudioEdit(action: EditAction.AudioEdit): Project = when (action) {
    is EditAction.SetClipFades -> updateClipAudio(action.clipId) { clip ->
        val (fadeIn, fadeOut) = clampFades(action.fadeInMs, action.fadeOutMs, clip.timelineDurationMs())
        clip.copy(fadeInMs = fadeIn, fadeOutMs = fadeOut)
    }
    is EditAction.SetOriginalAudioMuted ->
        if (originalAudioMuted == action.muted) this else copy(originalAudioMuted = action.muted)
    is EditAction.ExtractAudio -> extractAudio(action)
    is EditAction.AddAudio -> copy(audioClips = audioClips + action.audio.normalized(totalDurationMs()))
    is EditAction.DeleteAudio -> audioClips.filterNot { it.id == action.audioId }
        .let { if (it.size == audioClips.size) this else copy(audioClips = it) }
    is EditAction.SplitAudio -> splitAudio(action)
    is EditAction.DuplicateAudio -> audioById(action.audioId)?.let { original ->
        val copy = original.copy(id = action.newAudioId, timelineStartMs = original.timelineEndMs())
        copy(audioClips = audioClips + copy.normalized(totalDurationMs()))
    } ?: this
    is EditAction.ReplaceAudio -> updateAudio(action.audioId) {
        val length = it.trimEndMs - it.trimStartMs
        it.copy(
            uri = action.uri,
            sourceDurationMs = action.sourceDurationMs,
            displayName = action.displayName,
            kind = AudioKind.MUSIC,
            trimStartMs = 0L,
            trimEndMs = minOf(length, action.sourceDurationMs),
            beatsMs = emptyList(),
        )
    }
    is EditAction.SetAudioTiming -> updateAudio(action.audioId) {
        it.copy(timelineStartMs = action.timelineStartMs, trimStartMs = action.trimStartMs, trimEndMs = action.trimEndMs)
    }
    is EditAction.SetAudioVolume -> updateAudio(action.audioId) { it.copy(volume = action.volume.coerceIn(0f, MAX_VOLUME)) }
    is EditAction.SetAudioFades -> updateAudio(action.audioId) { it.copy(fadeInMs = action.fadeInMs, fadeOutMs = action.fadeOutMs) }
    is EditAction.SetAudioSpeed -> updateAudio(action.audioId) { it.copy(speed = action.speed.coerceIn(MIN_AUDIO_SPEED, MAX_AUDIO_SPEED)) }
    is EditAction.SetVoiceEffect -> updateAudio(action.audioId) { it.copy(voiceEffect = action.effect) }
    is EditAction.ToggleBeat -> updateAudio(action.audioId) { it.toggledBeat(action.atTimelineMs) }
    is EditAction.ClearBeats -> updateAudio(action.audioId) { it.copy(beatsMs = emptyList()) }
}

private fun Project.audioById(id: String) = audioClips.firstOrNull { it.id == id }

/** Replaces the audio clip, re-normalizing it; same instance when nothing changed. */
private fun Project.updateAudio(id: String, transform: (AudioClip) -> AudioClip): Project {
    val index = audioClips.indexOfFirst { it.id == id }
    if (index < 0) return this
    val updated = transform(audioClips[index]).normalized(totalDurationMs())
    if (updated == audioClips[index]) return this
    return copy(audioClips = audioClips.toMutableList().also { it[index] = updated })
}

private fun Project.updateClipAudio(clipId: String, transform: (Clip) -> Clip): Project {
    val index = clips.indexOfFirst { it.id == clipId }
    if (index < 0) return this
    val updated = transform(clips[index])
    return if (updated == clips[index]) this else copy(clips = clips.toMutableList().also { it[index] = updated })
}

private fun Project.extractAudio(action: EditAction.ExtractAudio): Project {
    val index = clips.indexOfFirst { it.id == action.clipId }
    if (index < 0) return this
    val clip = clips[index]
    if (clip.type != ClipType.VIDEO) return this
    val audio = AudioClip(
        id = action.newAudioId,
        uri = clip.uri,
        sourceDurationMs = clip.sourceDurationMs,
        timelineStartMs = clipStartTimesMs()[index],
        trimStartMs = clip.trimStartMs,
        trimEndMs = clip.trimEndMs,
        volume = if (clip.muted) 1f else clip.volume,
        kind = AudioKind.EXTRACTED,
        speed = clip.speed,
        fadeInMs = clip.fadeInMs,
        fadeOutMs = clip.fadeOutMs,
    )
    return copy(
        clips = clips.toMutableList().also { it[index] = clip.copy(muted = true) },
        audioClips = audioClips + audio.normalized(totalDurationMs()),
    )
}

private fun Project.splitAudio(action: EditAction.SplitAudio): Project {
    val index = audioClips.indexOfFirst { it.id == action.audioId }
    if (index < 0) return this
    val audio = audioClips[index]
    val sourceSplit = audio.sourceAt(action.atTimelineMs)
    if (sourceSplit - audio.trimStartMs < MIN_CLIP_DURATION_MS || audio.trimEndMs - sourceSplit < MIN_CLIP_DURATION_MS) return this
    val first = audio.copy(trimEndMs = sourceSplit, fadeOutMs = 0L, beatsMs = audio.beatsMs.filter { it < sourceSplit })
    val second = audio.copy(
        id = action.newAudioId,
        timelineStartMs = action.atTimelineMs,
        trimStartMs = sourceSplit,
        fadeInMs = 0L,
        beatsMs = audio.beatsMs.filter { it >= sourceSplit },
    )
    val total = totalDurationMs()
    return copy(audioClips = audioClips.toMutableList().apply { this[index] = first.normalized(total); add(index + 1, second.normalized(total)) })
}

private fun AudioClip.toggledBeat(atTimelineMs: Long): AudioClip {
    val source = sourceAt(atTimelineMs)
    if (source !in trimStartMs..trimEndMs) return this
    val existing = beatsMs.firstOrNull { abs(it - source) <= BEAT_TOLERANCE_MS }
    return copy(beatsMs = if (existing != null) beatsMs - existing else (beatsMs + source).sorted())
}

/**
 * Clamps an audio clip: trim within the source (≥ [MIN_CLIP_DURATION_MS]), start in
 * `0..videoEnd - MIN` so it can't get lost past the end, fades fitting its length, beats in range.
 */
internal fun AudioClip.normalized(videoDurationMs: Long): AudioClip {
    val minLength = MIN_CLIP_DURATION_MS.coerceAtMost(sourceDurationMs)
    val trimStart = trimStartMs.coerceIn(0L, sourceDurationMs - minLength)
    val trimEnd = trimEndMs.coerceIn(trimStart + minLength, sourceDurationMs)
    val latestStart = (videoDurationMs - MIN_CLIP_DURATION_MS).coerceAtLeast(0L)
    val trimmed = copy(
        timelineStartMs = timelineStartMs.coerceIn(0L, latestStart),
        trimStartMs = trimStart,
        trimEndMs = trimEnd,
        speed = speed.coerceIn(MIN_AUDIO_SPEED, MAX_AUDIO_SPEED),
        beatsMs = beatsMs.filter { it in trimStart..trimEnd },
    )
    val (fadeIn, fadeOut) = clampFades(fadeInMs, fadeOutMs, trimmed.timelineDurationMs())
    return trimmed.copy(fadeInMs = fadeIn, fadeOutMs = fadeOut)
}

/** Non-negative fades that together fit in [lengthMs]; the fade-in keeps priority. Pure; shared with the spec. */
fun clampFades(fadeInMs: Long, fadeOutMs: Long, lengthMs: Long): Pair<Long, Long> {
    val fadeIn = fadeInMs.coerceIn(0L, lengthMs.coerceAtLeast(0L))
    val fadeOut = fadeOutMs.coerceIn(0L, (lengthMs - fadeIn).coerceAtLeast(0L))
    return fadeIn to fadeOut
}
