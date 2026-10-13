package com.savie.precut.model

import kotlinx.serialization.Serializable

const val DEFAULT_IMAGE_DURATION_MS = 3_000L

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
    /** 0f = muted. */
    val volume: Float = 1f,
    val filter: FilterType = FilterType.NONE,
    /** User-applied rotation: 0, 90, 180 or 270. */
    val rotationDegrees: Int = 0,
    /** Encoded frame size as stored in the file, before [sourceRotationDegrees] is applied. */
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    /** Rotation metadata of the source file. */
    val sourceRotationDegrees: Int = 0,
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
