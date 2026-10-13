package com.savie.precut.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The project's cover (CapCut "Cover"): a frame of the video or a photo. */
@Serializable
sealed interface ProjectCover {
    /** The frame at timeline time [timelineMs]. */
    @Serializable
    @SerialName("frame")
    data class Frame(val timelineMs: Long) : ProjectCover

    /** A photo; [rotationDegrees] is its EXIF rotation. */
    @Serializable
    @SerialName("image")
    data class Image(val uri: String, val rotationDegrees: Int = 0) : ProjectCover
}

/** The picture a cover shows: a frame at [sourceMs] of a video, or a photo. */
data class CoverSource(val uri: String, val type: ClipType, val sourceMs: Long, val rotationDegrees: Int)

/** What the project's cover shows: the chosen cover, else the first clip's first frame. */
fun Project.coverSource(): CoverSource? = when (val chosen = cover) {
    is ProjectCover.Image -> CoverSource(chosen.uri, ClipType.IMAGE, 0L, chosen.rotationDegrees)
    is ProjectCover.Frame -> clipAt(chosen.timelineMs.coerceIn(0L, totalDurationMs()))?.let { position ->
        val clip = clips[position.index]
        CoverSource(clip.uri, clip.type, clip.timelineOffsetToSourceMs(position.offsetMs), clip.sourceRotationDegrees)
    }
    null -> clips.firstOrNull()?.let { CoverSource(it.uri, it.type, it.trimStartMs, it.sourceRotationDegrees) }
}
