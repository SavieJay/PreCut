package com.savie.precut.model.frame

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineOffsetToSourceMs
import kotlinx.serialization.Serializable

/**
 * Remove background (Phase 22A): the person stays and everything else turns transparent (the
 * canvas shows on the main track, what's below on an overlay). The outline comes from a matte
 * worked out on the phone from [sourceUri] between [startMs] and [endMs] (source time); the clip's
 * media are untouched, and moments the matte doesn't cover show uncut.
 */
@Serializable
data class Cutout(
    /** The matte file in app storage. */
    val matteUri: String,
    /** The media the matte was made from: the clip's own, or the original of a reversed clip. */
    val sourceUri: String,
    val startMs: Long,
    val endMs: Long,
    /** 0 (sharp) .. 1 (soft) edge. */
    val softness: Float = DEFAULT_SOFTNESS,
    /** −1 (tighter) .. 1 (looser) around the person. */
    val edge: Float = 0f,
) {
    companion object {
        const val DEFAULT_SOFTNESS = 0.3f

        /** A matte frame stands for moments this close to its range too (a frame's length at 30 fps). */
        const val RANGE_SLACK_MS = 34L
    }
}

/**
 * The matte time (ms) for source time [sourceMs] of the clip's media; null where the matte has no
 * picture (another file after Replace, or outside its range), which shows the clip uncut. A
 * reversed clip uses its original's matte backwards; a photo has one matte picture at 0.
 */
fun Clip.matteMsAt(sourceMs: Long): Long? {
    val cut = cutout ?: return null
    if (type == ClipType.IMAGE) return 0L.takeIf { cut.sourceUri == uri }
    val original = reversedFrom
    val mapped = when {
        cut.sourceUri == uri -> sourceMs
        original != null && cut.sourceUri == original.uri -> original.trimStartMs + (sourceDurationMs - sourceMs)
        else -> return null
    }
    return mapped.takeIf { it in (cut.startMs - Cutout.RANGE_SLACK_MS)..(cut.endMs + Cutout.RANGE_SLACK_MS) }
}

/** [matteMsAt] for [offsetMs] into the clip on the timeline (through its trim, speed or curve). */
fun Clip.matteMsAtOffset(offsetMs: Long): Long? =
    matteMsAt(timelineOffsetToSourceMs(offsetMs.coerceIn(0L, timelineDurationMs())))

/**
 * Whether the matte still covers everything the clip shows: false after Replace, Reverse of a clip
 * whose matte came from the reversed copy, or a trim past the matte's range (the panel offers Update).
 */
fun Clip.cutoutCoversClip(): Boolean {
    if (cutout == null) return false
    if (type == ClipType.IMAGE) return matteMsAt(0L) != null
    return matteMsAt(trimStartMs) != null && matteMsAt(trimEndMs) != null
}

/** The source range of the clip's current media a new matte should cover. */
fun Clip.cutoutRangeMs(): LongRange = if (type == ClipType.IMAGE) 0L..0L else trimStartMs..trimEndMs
