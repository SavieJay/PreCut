package com.savie.precut.model.edit

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.AudioClip
import com.savie.precut.model.Clip
import com.savie.precut.model.FilterType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.VoiceEffect

/**
 * Every change a user can make to a project. Applied by [EditReducer]. New ids are passed in so
 * the reducer stays pure and deterministic.
 */
sealed interface EditAction {
    /** Inserts [clips] at [atIndex] (clamped), or at the end when null. */
    data class AddClips(val clips: List<Clip>, val atIndex: Int? = null) : EditAction

    data class DeleteClip(val clipId: String) : EditAction

    /** Splits the clip under timeline time [atTimelineMs]; the second half gets [newClipId]. */
    data class SplitClip(val atTimelineMs: Long, val newClipId: String) : EditAction

    /** Moves the clip at index [from] so it ends up at index [to]. */
    data class MoveClip(val from: Int, val to: Int) : EditAction

    /** Inserts a copy right after the original. */
    data class DuplicateClip(val clipId: String, val newClipId: String) : EditAction

    /** Sets a clip's trim range in source time; values are clamped to a valid range. */
    data class TrimClip(val clipId: String, val trimStartMs: Long, val trimEndMs: Long) : EditAction

    /** Playback speed, clamped to [MIN_SPEED]..[MAX_SPEED]. Ignored for photos. */
    data class SetSpeed(val clipId: String, val speed: Float) : EditAction

    data class SetFilter(val clipId: String, val filter: FilterType) : EditAction

    /** Rotates the clip a further 90° clockwise. */
    data class RotateClip(val clipId: String) : EditAction

    data class SetAspectRatio(val aspectRatio: AspectRatio) : EditAction

    /** Adds a text overlay; its time range is clamped to the timeline. */
    data class AddText(val overlay: TextOverlay) : EditAction

    /** Replaces the overlay with the same id; its time range and position are clamped. */
    data class UpdateText(val overlay: TextOverlay) : EditAction

    data class DeleteText(val overlayId: String) : EditAction

    /** Clip audio gain, clamped to 0..MAX_VOLUME. */
    data class SetClipVolume(val clipId: String, val volume: Float) : EditAction

    data class SetClipMuted(val clipId: String, val muted: Boolean) : EditAction

    /**
     * Swaps a clip's media, keeping its position, length (as far as the new media allows) and
     * settings (speed for videos, filter, rotation, volume, fades).
     */
    data class ReplaceClip(val clipId: String, val uri: String, val info: MediaInfo) : EditAction

    /** Removes several clips and audio clips in one step (e.g. all missing media). */
    data class RemoveItems(val clipIds: Set<String>, val audioIds: Set<String>) : EditAction

    /** Audio edits, applied by `applyAudioEdit` (AudioEdits.kt). */
    sealed interface AudioEdit : EditAction

    data class SetClipFades(val clipId: String, val fadeInMs: Long, val fadeOutMs: Long) : AudioEdit

    /** CapCut's "Mute clip audio": silences every video clip's own sound. */
    data class SetOriginalAudioMuted(val muted: Boolean) : AudioEdit

    /** Moves a video clip's sound onto an audio lane (same timing) and mutes the clip. */
    data class ExtractAudio(val clipId: String, val newAudioId: String) : AudioEdit

    // Audio clips (multi-track; lanes are derived, see Project.audioLanes)

    data class AddAudio(val audio: AudioClip) : AudioEdit

    data class DeleteAudio(val audioId: String) : AudioEdit

    /** Splits at timeline time [atTimelineMs]; the second half gets [newAudioId]. */
    data class SplitAudio(val audioId: String, val atTimelineMs: Long, val newAudioId: String) : AudioEdit

    /** Inserts a copy right after the original. */
    data class DuplicateAudio(val audioId: String, val newAudioId: String) : AudioEdit

    /** Swaps the file, keeping the clip's start and length (as far as the new file allows). */
    data class ReplaceAudio(val audioId: String, val uri: String, val sourceDurationMs: Long, val displayName: String) : AudioEdit

    /** Moves and/or trims in one step; all values are clamped. */
    data class SetAudioTiming(val audioId: String, val timelineStartMs: Long, val trimStartMs: Long, val trimEndMs: Long) : AudioEdit

    data class SetAudioVolume(val audioId: String, val volume: Float) : AudioEdit

    data class SetAudioFades(val audioId: String, val fadeInMs: Long, val fadeOutMs: Long) : AudioEdit

    data class SetAudioSpeed(val audioId: String, val speed: Float) : AudioEdit

    data class SetVoiceEffect(val audioId: String, val effect: VoiceEffect) : AudioEdit

    /** Adds a beat at timeline time [atTimelineMs], or removes one already there. */
    data class ToggleBeat(val audioId: String, val atTimelineMs: Long) : AudioEdit

    data class ClearBeats(val audioId: String) : AudioEdit
}
