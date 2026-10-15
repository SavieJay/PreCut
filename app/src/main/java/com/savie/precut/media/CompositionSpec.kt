package com.savie.precut.media

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.OutputSize
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.CoverSource
import com.savie.precut.model.DEFAULT_SHORT_SIDE_PX
import com.savie.precut.model.coverSource
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.motion.MotionMath
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.overlayLanes
import com.savie.precut.model.placementWithin
import com.savie.precut.model.outputSize
import com.savie.precut.model.audioLanes
import com.savie.precut.model.edit.clampFades
import com.savie.precut.model.totalDurationMs

/**
 * Pure description of the Media3 composition for a project. Kept free of Media3/Android types so
 * the Project → composition mapping can be unit-tested on the JVM.
 */
data class CompositionSpec(
    val items: List<ItemSpec>,
    val outputSize: OutputSize,
    /** Applied once to the whole composition, in timeline time. */
    val texts: List<TextSpec> = emptyList(),
    /** Audio rows, each mixed in as its own audio-only sequence. */
    val audioLanes: List<List<AudioItemSpec>> = emptyList(),
    /** Shown full-frame over the first frame of the video (CapCut "add cover to video"). */
    val cover: CoverSpec? = null,
    /** Filter/adjust layers over the whole frame, in timeline time, in stacking order. */
    val lookLayers: List<LookLayerSpec> = emptyList(),
    /** Overlay rows, bottom (just above the main track) first. */
    val overlayLanes: List<OverlayLaneSpec> = emptyList(),
    /** The main track's length, which overlay rows are padded to. */
    val durationUs: Long = 0L,
)

/** An overlay in its row: [gapBeforeUs] of nothing, then [item] from [startUs] to [endUs]. */
data class OverlayItemSpec(val gapBeforeUs: Long, val item: ItemSpec, val startUs: Long, val endUs: Long)

/** One overlay row, padded with [trailingGapUs] to the video's end. */
data class OverlayLaneSpec(val items: List<OverlayItemSpec>, val trailingGapUs: Long)

/** A look layer: grades the composed frame between [startUs] and [endUs]. */
data class LookLayerSpec(val startUs: Long, val endUs: Long, val look: ClipLook)

/** The cover picture, shown until [endUs]. */
data class CoverSpec(val source: CoverSource, val endUs: Long)

/** How long the cover shows: one frame at 30 fps. */
const val COVER_DURATION_US = 1_000_000L / 30

/** One audio clip after placement: silence for [gapBeforeMs], then its (already cut) range. */
data class AudioItemSpec(
    val uri: String,
    val sourceDurationMs: Long,
    val gapBeforeMs: Long,
    val clipStartMs: Long,
    val clipEndMs: Long,
    val speed: Float,
    val sound: SoundSpec,
)

/** How an item's audio is shaped, in its timeline length [lengthMs]. */
data class SoundSpec(
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val lengthMs: Long = 0L,
    val pitch: Float = 1f,
)

/** Look shared by every item: grading, mirror, user rotation, crop, then placement in the frame. */
data class VisualSpec(
    /** Null when the clip isn't graded. */
    val look: ClipLook? = null,
    /** Clockwise, 0/90/180/270. */
    val rotationDegrees: Int = 0,
    val mirrored: Boolean = false,
    /** Of the mirrored, rotated picture; null when uncropped. */
    val crop: CropRect? = null,
    /** Null: plainly fitted on black (Media3's `Presentation`, the cheap path). */
    val frame: FrameSpec? = null,
    /** Transitions at the clip's start and end; null when it has none. */
    val transition: TransitionSpec? = null,
)

/** Where a clip sits in composition time, for effects that change over the clip. */
data class ClipTime(val startUs: Long, val lengthMs: Long)

/** In/Out/Combo animations of a clip starting at [time]. */
data class AnimationSpec(
    val time: ClipTime,
    val animationIn: ClipAnimation? = null,
    val animationOut: ClipAnimation? = null,
    val combo: ComboAnimation? = null,
)

/** The incoming transition's second half at the clip's start and the outgoing one's first half at its end (fitted durations). */
data class TransitionSpec(
    val time: ClipTime,
    val head: TransitionType? = null,
    val headMs: Long = 0L,
    val tail: TransitionType? = null,
    val tailMs: Long = 0L,
)

/**
 * Placement and background of a clip in the output frame. [liveKey] (preview only) names the clip
 * whose frame the preview may override while a gesture is in progress; null for export.
 */
data class FrameSpec(
    val fit: FrameFit = FrameFit.FIT,
    val transform: ClipTransform = ClipTransform.IDENTITY,
    val opacity: Float = 1f,
    val canvas: ClipCanvas = ClipCanvas(),
    val liveKey: String? = null,
    val animation: AnimationSpec? = null,
    /** An overlay: drawn on a transparent frame (no canvas) so the layers below show around it. */
    val transparent: Boolean = false,
)

sealed interface ItemSpec {
    val visual: VisualSpec

    data class Video(
        val uri: String,
        val sourceDurationMs: Long,
        val clipStartMs: Long,
        val clipEndMs: Long,
        val speed: Float = 1f,
        /** Replaces [speed] when set (stepped as in [com.savie.precut.model.speed.SpeedSteps]). */
        val speedCurve: SpeedCurve? = null,
        val keepPitch: Boolean = true,
        /** The clip's own sound; volume 0 when muted or when all original audio is muted. */
        val sound: SoundSpec = SoundSpec(),
        override val visual: VisualSpec = VisualSpec(),
    ) : ItemSpec

    data class Image(
        val uri: String,
        val durationMs: Long,
        override val visual: VisualSpec = VisualSpec(),
    ) : ItemSpec
}

/** A text overlay ready to render: pixel size in the output frame and a Media3 NDC anchor. */
data class TextSpec(
    val id: String,
    val text: String,
    val startUs: Long,
    val endUs: Long,
    /** -1 (left) .. 1 (right). */
    val anchorX: Float,
    /** -1 (bottom) .. 1 (top). */
    val anchorY: Float,
    val textSizePx: Float,
    val colorArgb: Int,
    val backgroundArgb: Int?,
    /** Clockwise. */
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
)

/**
 * Text sizes are "sp on a [TEXT_REFERENCE_WIDTH_DP]-wide screen", scaled to the output's short
 * side, so the editor's on-screen ghost and the export agree regardless of resolution.
 */
const val TEXT_REFERENCE_WIDTH_DP = 360f

fun textSizePx(textSizeSp: Float, frameShortSidePx: Int): Float = textSizeSp * frameShortSidePx / TEXT_REFERENCE_WIDTH_DP

/** Maps a 0..1 position (y down) to Media3's normalized device coordinates (y up). */
fun toNdc(xNorm: Float, yNorm: Float): Pair<Float, Float> = (xNorm * 2f - 1f) to (1f - yNorm * 2f)

/**
 * The composition for [project] with an output short side of [shortSidePx] (preview renders
 * smaller). [liveClipId] (preview only) marks the clip being transformed so its frame can follow a
 * gesture; it renders exactly as without it.
 */
fun compositionSpecOf(project: Project, shortSidePx: Int = DEFAULT_SHORT_SIDE_PX, liveClipId: String? = null): CompositionSpec {
    val outputSize = project.outputSize(shortSidePx)
    return CompositionSpec(
        items = project.clipStartTimesMs().let { starts ->
            project.clips.mapIndexed { index, clip ->
                itemSpecOf(clip, project.originalAudioMuted, live = clip.id == liveClipId, motion = motionOf(project, index, starts[index]))
            }
        },
        outputSize = outputSize,
        texts = project.textOverlays.filter { it.text.isNotBlank() }.map { textSpecOf(it, outputSize) },
        audioLanes = audioLanesOf(project),
        cover = project.coverSource()?.takeIf { project.coverInExport && project.cover != null }
            ?.let { CoverSpec(it, COVER_DURATION_US) },
        lookLayers = project.lookLayers.filterNot { it.look.isIdentity }
            .map { LookLayerSpec(it.startMs * 1_000, it.endMs * 1_000, it.look) },
        overlayLanes = overlayLanesOf(project, liveClipId),
        durationUs = project.totalDurationMs() * 1_000,
    )
}

/** Overlay rows cut at the video's end; empty rows (everything after the end) are dropped. */
private fun overlayLanesOf(project: Project, liveClipId: String?): List<OverlayLaneSpec> {
    val totalUs = project.totalDurationMs() * 1_000
    return project.overlayLanes().map { lane ->
        var cursorUs = 0L
        val items = lane.mapNotNull { overlay ->
            val clip = overlay.placementWithin(project.totalDurationMs()) ?: return@mapNotNull null
            val startUs = overlay.timelineStartMs * 1_000
            val length = clip.timelineDurationMs()
            val time = ClipTime(startUs, length)
            OverlayItemSpec(
                gapBeforeUs = startUs - cursorUs,
                item = itemSpecOf(clip, originalAudioMuted = false, live = clip.id == liveClipId, motion = ClipMotion(time, null), overlay = true),
                startUs = startUs,
                endUs = startUs + length * 1_000,
            ).also { cursorUs = it.endUs }
        }
        OverlayLaneSpec(items, (totalUs - cursorUs).coerceAtLeast(0L))
    }.filter { it.items.isNotEmpty() }
}

private fun audioLanesOf(project: Project): List<List<AudioItemSpec>> {
    val videoDuration = project.totalDurationMs()
    return project.audioLanes().map { lane ->
        var cursor = 0L
        lane.mapNotNull { audio ->
            val placement = audio.placementWithin(videoDuration) ?: return@mapNotNull null
            val (fadeIn, fadeOut) = clampFades(audio.fadeInMs, audio.fadeOutMs, placement.timelineDurationMs)
            AudioItemSpec(
                uri = audio.uri,
                sourceDurationMs = audio.sourceDurationMs,
                gapBeforeMs = placement.timelineStartMs - cursor,
                clipStartMs = placement.sourceStartMs,
                clipEndMs = placement.sourceEndMs,
                speed = audio.speed,
                sound = SoundSpec(audio.volume, fadeIn, fadeOut, placement.timelineDurationMs, audio.voiceEffect.pitch),
            ).also { cursor = placement.timelineStartMs + placement.timelineDurationMs }
        }
    }.filter { it.isNotEmpty() }
}

/** A clip's place on the timeline and the transitions at its two junctions. */
private class ClipMotion(val time: ClipTime, val transition: TransitionSpec?)

private fun motionOf(project: Project, index: Int, startMs: Long): ClipMotion {
    val clips = project.clips
    val clip = clips[index]
    val length = clip.timelineDurationMs()
    val time = ClipTime(startMs * 1_000, length)
    val head = clips.getOrNull(index - 1)?.transitionOut?.let { incoming ->
        incoming.type to MotionMath.fittedTransitionMs(incoming, clips[index - 1].timelineDurationMs(), length)
    }
    val tail = clip.transitionOut?.takeIf { index < clips.lastIndex }?.let { outgoing ->
        outgoing.type to MotionMath.fittedTransitionMs(outgoing, length, clips[index + 1].timelineDurationMs())
    }
    val transition = if (head == null && tail == null) {
        null
    } else {
        TransitionSpec(time, head?.first, head?.second ?: 0L, tail?.first, tail?.second ?: 0L)
    }
    return ClipMotion(time, transition)
}

private fun itemSpecOf(clip: Clip, originalAudioMuted: Boolean, live: Boolean, motion: ClipMotion, overlay: Boolean = false): ItemSpec {
    val visual = VisualSpec(
        look = clip.look.takeUnless { it.isIdentity },
        rotationDegrees = clip.rotationDegrees,
        mirrored = clip.mirrored,
        crop = clip.crop?.takeUnless { it.isFull },
        frame = frameSpecOf(clip, live, motion.time, overlay),
        transition = motion.transition,
    )
    return when (clip.type) {
        ClipType.VIDEO -> ItemSpec.Video(
            uri = clip.uri,
            sourceDurationMs = clip.sourceDurationMs,
            clipStartMs = clip.trimStartMs,
            clipEndMs = clip.trimEndMs,
            speed = clip.speed,
            speedCurve = clip.speedCurve,
            keepPitch = clip.keepPitch,
            sound = clip.timelineDurationMs().let { length ->
                val (fadeIn, fadeOut) = clampFades(clip.fadeInMs, clip.fadeOutMs, length)
                SoundSpec(if (clip.muted || originalAudioMuted) 0f else clip.volume, fadeIn, fadeOut, length)
            },
            visual = visual,
        )
        ClipType.IMAGE -> ItemSpec.Image(uri = clip.uri, durationMs = clip.trimEndMs - clip.trimStartMs, visual = visual)
    }
}

/**
 * Null for a plainly fitted, opaque, still clip on black, unless the preview needs it [live].
 * Overlays always get one, on a transparent frame.
 */
private fun frameSpecOf(clip: Clip, live: Boolean, time: ClipTime, overlay: Boolean): FrameSpec? {
    val animated = clip.animationIn != null || clip.animationOut != null || clip.animationCombo != null
    val plain = clip.fit == FrameFit.FIT && clip.transform.isIdentity && clip.opacity >= 1f && clip.canvas.isDefault && !animated
    if (plain && !live && !overlay) return null
    return FrameSpec(
        clip.fit, clip.transform, clip.opacity.coerceIn(0f, 1f), clip.canvas,
        liveKey = clip.id.takeIf { live },
        animation = if (animated) AnimationSpec(time, clip.animationIn, clip.animationOut, clip.animationCombo) else null,
        transparent = overlay,
    )
}

private fun textSpecOf(overlay: TextOverlay, outputSize: OutputSize): TextSpec {
    val (x, y) = toNdc(overlay.xNorm, overlay.yNorm)
    return TextSpec(
        id = overlay.id,
        text = overlay.text,
        startUs = overlay.startMs * 1_000,
        endUs = overlay.endMs * 1_000,
        anchorX = x,
        anchorY = y,
        textSizePx = textSizePx(overlay.textSizeSp, minOf(outputSize.width, outputSize.height)),
        colorArgb = overlay.colorArgb,
        backgroundArgb = overlay.backgroundArgb,
        rotationDegrees = overlay.rotationDegrees,
        opacity = overlay.opacity.coerceIn(0f, 1f),
    )
}

/** Whether a text overlay is on screen at composition time [presentationTimeUs]. */
fun TextSpec.isVisibleAt(presentationTimeUs: Long): Boolean = presentationTimeUs in startUs until endUs
