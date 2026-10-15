package com.savie.precut.model.edit

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.AudioClip
import com.savie.precut.model.Clip
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.OverlayClip
import com.savie.precut.model.ProjectCover
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.VoiceEffect
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.CropRatio
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.look.LookLayer
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.speed.SpeedCurve

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

    /** Playback speed, clamped to [MIN_CLIP_SPEED]..[MAX_CLIP_SPEED]; clears a speed curve. Ignored for photos. */
    data class SetSpeed(val clipId: String, val speed: Float) : EditAction

    /** A speed curve (null goes back to the constant speed); normalized. Ignored for photos. */
    data class SetSpeedCurve(val clipId: String, val curve: SpeedCurve?) : EditAction

    /** Whether a sped-up/slowed clip keeps its audio pitch. */
    data class SetKeepPitch(val clipId: String, val keepPitch: Boolean) : EditAction

    /** Filter, intensity, Adjust, HSL and Curves of a clip. */
    data class SetClipLook(val clipId: String, val look: ClipLook) : EditAction

    /** Look layer edits (time-ranged filter/adjust over everything), applied by `applyLayerEdit`. */
    sealed interface LayerEdit : EditAction

    data class AddLookLayer(val layer: LookLayer) : LayerEdit

    /** Replaces the layer with the same id (look and timing); timing is clamped. */
    data class UpdateLookLayer(val layer: LookLayer) : LayerEdit

    data class DeleteLookLayer(val layerId: String) : LayerEdit

    data class SplitLookLayer(val layerId: String, val atMs: Long, val newId: String) : LayerEdit

    /** A copy starting where the original ends. */
    data class DuplicateLookLayer(val layerId: String, val newId: String) : LayerEdit

    /** Frame edits (crop, mirror, rotate, transform, opacity, canvas), applied by `applyFrameEdit`. */
    sealed interface FrameEdit : EditAction

    /** Rotates the clip a further 90° clockwise; its crop turns with it. */
    data class RotateClip(val clipId: String) : FrameEdit

    /** Flips what the user sees left–right, whatever the rotation; the crop flips with it. */
    data class ToggleMirror(val clipId: String) : FrameEdit

    /** Crop mode's result: crop (null or full clears it), Ratio chip, mirror and rotation in one step. */
    data class SetCrop(
        val clipId: String,
        val crop: CropRect?,
        val ratio: CropRatio,
        val mirrored: Boolean,
        val rotationDegrees: Int,
    ) : FrameEdit

    /** Fit/Fill and the on-preview transform; the scale is clamped. */
    data class SetClipTransform(val clipId: String, val fit: FrameFit, val transform: ClipTransform) : FrameEdit

    /** Clamped to 0..1. */
    data class SetOpacity(val clipId: String, val opacity: Float) : FrameEdit

    data class SetCanvas(val clipId: String, val canvas: ClipCanvas) : FrameEdit

    /** Motion edits (transitions, animations, freeze frame), applied by `applyMotionEdit`. */
    sealed interface MotionEdit : EditAction

    /** The transition from this clip into the next (null removes it); the duration is clamped. */
    data class SetTransition(val clipId: String, val transition: ClipTransition?) : MotionEdit

    /** "Apply to all": the same transition at every junction. */
    data class SetAllTransitions(val transition: ClipTransition?) : MotionEdit

    /** In, Out and Combo together; a combo clears In/Out. Durations and speed are clamped. */
    data class SetAnimations(
        val clipId: String,
        val animationIn: ClipAnimation?,
        val animationOut: ClipAnimation?,
        val combo: ComboAnimation?,
    ) : MotionEdit

    /**
     * Freeze frame: splits the clip under [atTimelineMs] (the second part gets [secondPartId]) and
     * puts [still] (a photo clip of that frame) between the parts. Near a clip's edge it's inserted
     * before or after the clip without splitting. The still takes the clip's look and frame.
     */
    data class FreezeFrame(val atTimelineMs: Long, val secondPartId: String, val still: Clip) : MotionEdit

    /** Plays [reversedUri] (a reversed copy of the clip's range, described by [info]) instead of the media. */
    data class ReverseClip(val clipId: String, val reversedUri: String, val info: MediaInfo) : MotionEdit

    /** Back to the original media; a trim made while reversed maps back onto it. */
    data class UnreverseClip(val clipId: String) : MotionEdit

    data class SetAspectRatio(val aspectRatio: AspectRatio) : EditAction

    /** Renames the project (trimmed; blank shows the date fallback). */
    data class SetName(val name: String) : EditAction

    /** Adds a text overlay; its time range is clamped to the timeline. */
    data class AddText(val overlay: TextOverlay) : EditAction

    /** Replaces the overlay with the same id; its time range and position are clamped. */
    data class UpdateText(val overlay: TextOverlay) : EditAction

    data class DeleteText(val overlayId: String) : EditAction

    /** Splits the text at timeline time [atMs]; the second part gets [newId]. */
    data class SplitText(val overlayId: String, val atMs: Long, val newId: String) : EditAction

    /** A copy starting where the original ends (same length; clamped to the timeline). */
    data class DuplicateText(val overlayId: String, val newId: String) : EditAction

    /** Sets (or with null clears) the project cover, and whether it's the export's first frame. */
    data class SetCover(val cover: ProjectCover?, val inExport: Boolean) : EditAction

    /** Several edits applied in order as one undo step (multi-select, "Apply to all"). */
    data class Batch(val actions: List<EditAction>) : EditAction

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

    /** Overlay (picture-in-picture) edits, applied by `applyOverlayEdit`. Clip settings use the clip actions by id. */
    sealed interface OverlayEdit : EditAction

    data class AddOverlay(val overlay: OverlayClip) : OverlayEdit

    /** Moves and/or trims in one step; the start stays ≥ 0 and the trim inside the source. */
    data class SetOverlayTiming(val overlayId: String, val timelineStartMs: Long, val trimStartMs: Long, val trimEndMs: Long) : OverlayEdit

    /** Splits at timeline time [atMs]; the second part gets [newId]. */
    data class SplitOverlay(val overlayId: String, val atMs: Long, val newId: String) : OverlayEdit

    /** A copy starting where the original ends. */
    data class DuplicateOverlay(val overlayId: String, val newId: String) : OverlayEdit

    data class DeleteOverlay(val overlayId: String) : OverlayEdit

    /** "Switch to main track": the overlay's clip goes into the main track at [atIndex] (clamped). */
    data class OverlayToMain(val overlayId: String, val atIndex: Int) : OverlayEdit

    /** "Switch to overlay": a main clip becomes an overlay at its current time (not the only main clip). */
    data class ClipToOverlay(val clipId: String) : OverlayEdit

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
