package com.savie.precut.model

import com.savie.precut.model.frame.ClipTransform
import kotlinx.serialization.Serializable

/** How big a new overlay is, relative to fitting the frame. */
const val DEFAULT_OVERLAY_SCALE = 0.6f

/**
 * A picture-in-picture clip (P-01) placed at [timelineStartMs] above the main track. Its [clip]
 * carries the same look, frame and motion settings as a main clip (transitions don't apply).
 */
@Serializable
data class OverlayClip(val clip: Clip, val timelineStartMs: Long) {
    val id: String get() = clip.id
    val timelineEndMs: Long get() = timelineStartMs + clip.timelineDurationMs()
}

/** A new overlay of picked media at [atMs]: fitted, 60% size, centred. */
fun overlayFromMedia(id: String, uri: String, info: MediaInfo, atMs: Long): OverlayClip =
    OverlayClip(clipFromMedia(id, uri, info).copy(transform = ClipTransform(scale = DEFAULT_OVERLAY_SCALE)), atMs.coerceAtLeast(0L))

/** Overlays stacked into rows; row 0 sits just above the main track, later rows draw on top. */
fun Project.overlayLanes(): List<List<OverlayClip>> =
    stackIntoLanes(overlays, OverlayClip::timelineStartMs, { it.timelineEndMs }, OverlayClip::id)

/** The main clip or overlay clip with [id]. */
fun Project.clipById(id: String?): Clip? =
    id?.let { clips.firstOrNull { c -> c.id == it } ?: overlays.firstOrNull { o -> o.id == it }?.clip }

/** Where the main clip or overlay with [id] plays on the timeline, or null if there's none. */
fun Project.clipTimelineRange(id: String): LongRange? {
    val index = clips.indexOfFirst { it.id == id }
    if (index >= 0) {
        val start = clipStartTimesMs()[index]
        return start until start + clips[index].timelineDurationMs()
    }
    return overlayById(id)?.let { it.timelineStartMs until it.timelineEndMs }
}

fun Project.overlayById(id: String?): OverlayClip? = id?.let { overlays.firstOrNull { o -> o.id == it } }

/**
 * Where an overlay plays inside a video of [videoDurationMs]: the overlay's trim, cut at the
 * video's end; null when it starts at or after the end.
 */
fun OverlayClip.placementWithin(videoDurationMs: Long): Clip? {
    val available = videoDurationMs - timelineStartMs
    if (available <= 0L) return null
    if (clip.timelineDurationMs() <= available) return clip
    return clip.copy(trimEndMs = clip.timelineOffsetToSourceMs(available).coerceAtLeast(clip.trimStartMs + 1))
}
