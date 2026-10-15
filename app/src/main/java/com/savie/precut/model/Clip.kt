package com.savie.precut.model

import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.CropRatio
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.speed.SpeedCurve
import kotlinx.serialization.Serializable

const val DEFAULT_IMAGE_DURATION_MS = 3_000L

/** Highest volume for clips and music (200%). */
const val MAX_VOLUME = 2f

/** Photos have no intrinsic length; this is how far their trim range can be extended. */
const val MAX_IMAGE_DURATION_MS = 60_000L

@Serializable
data class Clip(
    val id: String,
    val uri: String,
    val type: ClipType,
    /** For IMAGE: [MAX_IMAGE_DURATION_MS]; the trim range is the display duration. */
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0,
    /** Exclusive. */
    val trimEndMs: Long,
    val speed: Float = 1f,
    /** Varying speed across the clip (videos only); replaces [speed] while set. */
    val speedCurve: SpeedCurve? = null,
    /** Keeps the audio's pitch when [speed] isn't 1 (CapCut's pitch toggle). */
    val keepPitch: Boolean = true,
    /** Gain for the clip's own audio, 0..[MAX_VOLUME]. */
    val volume: Float = 1f,
    /** Silences the clip without losing [volume]. */
    val muted: Boolean = false,
    /** Fades of the clip's own sound, in timeline time. */
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    /** Filter, Adjust, HSL and Curves. */
    val look: ClipLook = ClipLook(),
    /** User-applied rotation: 0, 90, 180 or 270. */
    val rotationDegrees: Int = 0,
    /** Flipped left–right before [rotationDegrees]. */
    val mirrored: Boolean = false,
    /** Crop of the mirrored, rotated picture; null shows all of it. */
    val crop: CropRect? = null,
    /** The crop's Ratio chip, kept so crop mode reopens with it. */
    val cropRatio: CropRatio = CropRatio.FREE,
    /** Fitted or filled into the project frame, before [transform]. */
    val fit: FrameFit = FrameFit.FIT,
    /** Move, scale and rotate on the preview. */
    val transform: ClipTransform = ClipTransform(),
    /** 0..1, over the [canvas]. */
    val opacity: Float = 1f,
    /** What shows around and under the clip. */
    val canvas: ClipCanvas = ClipCanvas(),
    /** Into the next clip; ignored on the last clip. */
    val transitionOut: ClipTransition? = null,
    val animationIn: ClipAnimation? = null,
    val animationOut: ClipAnimation? = null,
    /** Loops for the whole clip and replaces In/Out. */
    val animationCombo: ComboAnimation? = null,
    /** Set while the clip plays a reversed copy ([uri]); what to restore when it's reversed back. */
    val reversedFrom: ReverseSource? = null,
    /** Encoded frame size as stored in the file, before [sourceRotationDegrees] is applied. */
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    /** Rotation metadata of the source file. */
    val sourceRotationDegrees: Int = 0,
)

/** A reversed clip's original media and range (T-19). */
@Serializable
data class ReverseSource(
    val uri: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sourceRotationDegrees: Int,
)

/** Media facts read from a source file at import time. */
data class MediaInfo(
    val type: ClipType,
    /** 0 for images. */
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)

fun clipFromMedia(id: String, uri: String, info: MediaInfo): Clip {
    val (sourceDuration, shownDuration) = when (info.type) {
        ClipType.VIDEO -> info.durationMs to info.durationMs
        ClipType.IMAGE -> MAX_IMAGE_DURATION_MS to DEFAULT_IMAGE_DURATION_MS
    }
    return Clip(
        id = id,
        uri = uri,
        type = info.type,
        sourceDurationMs = sourceDuration,
        trimEndMs = shownDuration,
        sourceWidth = info.width,
        sourceHeight = info.height,
        sourceRotationDegrees = info.rotationDegrees,
    )
}
