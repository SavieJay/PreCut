package com.savie.precut.media

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.OutputSize
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.CoverSource
import com.savie.precut.model.DEFAULT_SHORT_SIDE_PX
import com.savie.precut.model.coverSource
import com.savie.precut.model.frame.ChromaKey
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.keyframe.ClipKeyframe
import com.savie.precut.model.keyframe.TextKeyframe
import com.savie.precut.model.edit.keyframesOnTimeline
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.motion.MotionMath
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.model.speed.SpeedSteps
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.overlapsBeforeMs
import com.savie.precut.model.timelineOffsetToSourceMs
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.overlayLanes
import com.savie.precut.model.placementWithin
import com.savie.precut.model.outputSize
import com.savie.precut.model.audioLanes
import com.savie.precut.model.edit.clampFades
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.effect.EffectSettings

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
    /** Effect layers, over the graded video and under texts and stickers. */
    val effectLayers: List<EffectLayerSpec> = emptyList(),
    /** Overlay rows, bottom (just above the main track) first. */
    val overlayLanes: List<OverlayLaneSpec> = emptyList(),
    /** The main track's length, which overlay rows are padded to. */
    val durationUs: Long = 0L,
    /** Overlapping transitions (P-06): the incoming clip's head drawn over the outgoing clip's end. */
    val transitions: List<TransitionLayerSpec> = emptyList(),
    /** The incoming clips' heads' sound during overlapping transitions; null when none has any. */
    val transitionAudio: OverlayLaneSpec? = null,
)

/**
 * An overlapping transition: from [startUs] to [endUs] the main track still shows the outgoing
 * clip while [item] (the whole incoming clip, timed from [startUs]) comes in as [type] says.
 */
data class TransitionLayerSpec(val item: ItemSpec, val startUs: Long, val endUs: Long, val type: TransitionType)

/** An overlay in its row: [gapBeforeUs] of nothing, then [item] from [startUs] to [endUs]. */
data class OverlayItemSpec(val gapBeforeUs: Long, val item: ItemSpec, val startUs: Long, val endUs: Long)

/** One overlay row, padded with [trailingGapUs] to the video's end. */
data class OverlayLaneSpec(val items: List<OverlayItemSpec>, val trailingGapUs: Long)

/** A look layer: grades the composed frame between [startUs] and [endUs]. */
data class LookLayerSpec(val startUs: Long, val endUs: Long, val look: ClipLook)

/** An effect layer: changes the composed frame between [startUs] and [endUs], animating from [startUs]. */
data class EffectLayerSpec(val startUs: Long, val endUs: Long, val effect: EffectSettings)

/** A clip's own effect, animating from the clip's start ([startUs], composition time). */
data class ClipEffectSpec(val effect: EffectSettings, val startUs: Long)

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
    /** Volume keyframes (times into the clip, timeline ms); they replace [volume] while set. */
    val volumeKeyframes: List<ClipKeyframe> = emptyList(),
    /** Where this item starts in its clip (timeline ms), for keyframe times; non-zero after a cut head. */
    val keyframeOffsetMs: Long = 0L,
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
    /** Keyed on the source colours, in the look pass; null without a key. */
    val chroma: ChromaKey? = null,
    /** Keyframes animating filter strength, Adjust and chroma strength, timed from [keyframesStartUs]. */
    val lookKeyframes: List<ClipKeyframe> = emptyList(),
    /** Composition time of the clip's start (keyframe times are offsets from it). */
    val keyframesStartUs: Long = 0L,
    /** The clip's own effect, last in its chain; null without one. */
    val effect: ClipEffectSpec? = null,
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
    val mask: ClipMask? = null,
    /** Keyframes (times into the clip, timeline ms) animating transform, opacity and mask. */
    val keyframes: List<ClipKeyframe> = emptyList(),
    /** Composition time of the clip's start. */
    val clipStartUs: Long = 0L,
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
        /** The source range the curve is laid over, when this item is only part of its clip; null: this item's range. */
        val curveRangeMs: LongRange? = null,
        val keepPitch: Boolean = true,
        /** The clip's own sound; volume 0 when muted or when all original audio is muted. */
        val sound: SoundSpec = SoundSpec(),
        override val visual: VisualSpec = VisualSpec(),
    ) : ItemSpec

    data class Image(
        val uri: String,
        val durationMs: Long,
        override val visual: VisualSpec = VisualSpec(),
        /** EXIF rotation that turns the picture upright. */
        val rotationDegrees: Int = 0,
    ) : ItemSpec
}

/**
 * Source time (µs) shown [localUs] into the item: its trim start plus the elapsed time at its speed
 * or through its curve's [steps] (pass them in to avoid rebuilding them per frame).
 */
fun ItemSpec.Video.sourceUsAt(localUs: Long, steps: SpeedSteps? = speedStepsOf(this)): Long {
    val startUs = clipStartMs * 1_000
    val endUs = clipEndMs * 1_000
    val offset = steps?.sourceAtUs(localUs.toDouble()) ?: (localUs * speed.toDouble()).toLong()
    return (startUs + offset).coerceIn(startUs, endUs)
}

/** A curved item's speed steps over its curve range, as Media3 plays them; null at constant speed. */
fun speedStepsOf(video: ItemSpec.Video): SpeedSteps? {
    val range = video.curveRangeMs ?: (video.clipStartMs..video.clipEndMs)
    return video.speedCurve?.let { SpeedSteps.of(it, (range.last - range.first) * 1_000) }
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
    /** Animated place, size, turn and opacity, times into the text; the bitmap is drawn at [textSizeSp]. */
    val keyframes: List<TextKeyframe> = emptyList(),
    val textSizeSp: Float = 0f,
    /** The text's styling (font, effects, spans, animations), read by the renderer. */
    val style: TextOverlay = TextOverlay("", text, 0L, 0L, textSizeSp = textSizeSp),
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
    val starts = project.clipStartTimesMs()
    val overlaps = project.overlapsBeforeMs()
    val fullItems = project.clips.mapIndexed { index, clip ->
        itemSpecOf(clip, project.originalAudioMuted, live = clip.id == liveClipId, motion = motionOf(project, index, starts[index]))
    }
    val transitions = project.clips.indices.filter { overlaps[it] > 0 }.map { index ->
        val item = fullItems[index]
        TransitionLayerSpec(
            // Drawn as a whole frame (with its canvas), even when the clip is plainly fitted.
            item = item.withFrame(item.visual.frame ?: FrameSpec()),
            startUs = starts[index] * 1_000,
            endUs = (starts[index] + overlaps[index]) * 1_000,
            type = checkNotNull(project.clips[index - 1].transitionOut).type,
        )
    }
    return CompositionSpec(
        items = project.clips.mapIndexed { index, clip -> fullItems[index].withoutHead(clip, overlaps[index]) },
        transitions = transitions,
        transitionAudio = transitionAudioOf(project, fullItems, starts, overlaps),
        outputSize = outputSize,
        texts = project.textOverlays.filter { it.text.isNotBlank() || it.sticker != null }.map { textSpecFor(it, outputSize) },
        audioLanes = audioLanesOf(project),
        cover = project.coverSource()?.takeIf { project.coverInExport && project.cover != null }
            ?.let { CoverSpec(it, COVER_DURATION_US) },
        lookLayers = project.lookLayers.filterNot { it.look.isIdentity }
            .map { LookLayerSpec(it.startMs * 1_000, it.endMs * 1_000, it.look) },
        effectLayers = project.effectLayers.map { EffectLayerSpec(it.startMs * 1_000, it.endMs * 1_000, it.effect) },
        overlayLanes = overlayLanesOf(project, liveClipId),
        durationUs = project.totalDurationMs() * 1_000,
    )
}

/** [this] item without its first [overlapMs] (shown by a transition layer instead); unchanged at 0. */
private fun ItemSpec.withoutHead(clip: Clip, overlapMs: Long): ItemSpec {
    if (overlapMs <= 0L) return this
    return when (this) {
        is ItemSpec.Video -> copy(
            clipStartMs = clip.timelineOffsetToSourceMs(overlapMs),
            curveRangeMs = speedCurve?.let { clipStartMs..clipEndMs },
            sound = sound.copy(keyframeOffsetMs = overlapMs, lengthMs = (sound.lengthMs - overlapMs).coerceAtLeast(0L)),
        )
        is ItemSpec.Image -> copy(durationMs = (durationMs - overlapMs).coerceAtLeast(1L))
    }
}

private fun ItemSpec.withFrame(frame: FrameSpec): ItemSpec = when (this) {
    is ItemSpec.Video -> copy(visual = visual.copy(frame = frame))
    is ItemSpec.Image -> copy(visual = visual.copy(frame = frame))
}

/** The incoming clips' heads' sound, faded in across each overlap; null without any. */
private fun transitionAudioOf(project: Project, items: List<ItemSpec>, starts: List<Long>, overlaps: List<Long>): OverlayLaneSpec? {
    var cursorUs = 0L
    val heads = project.clips.indices.filter { overlaps[it] > 0 }.mapNotNull { index ->
        val video = items[index] as? ItemSpec.Video ?: return@mapNotNull null
        if (video.sound.volume <= 0f) return@mapNotNull null
        val clip = project.clips[index]
        val startUs = starts[index] * 1_000
        val head = video.copy(
            clipEndMs = clip.timelineOffsetToSourceMs(overlaps[index]),
            curveRangeMs = video.speedCurve?.let { video.clipStartMs..video.clipEndMs },
            sound = video.sound.copy(fadeInMs = overlaps[index], fadeOutMs = 0L, lengthMs = overlaps[index], volumeKeyframes = emptyList()),
        )
        OverlayItemSpec(gapBeforeUs = startUs - cursorUs, item = head, startUs = startUs, endUs = startUs + overlaps[index] * 1_000)
            .also { cursorUs = it.endUs }
    }
    if (heads.isEmpty()) return null
    return OverlayLaneSpec(heads, (project.totalDurationMs() * 1_000 - cursorUs).coerceAtLeast(0L))
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
    // Overlapping transitions are drawn by their own layer, not on the clips' edges.
    val head = clips.getOrNull(index - 1)?.transitionOut?.takeUnless { it.type.overlapping }?.let { incoming ->
        incoming.type to MotionMath.fittedTransitionMs(incoming, clips[index - 1].timelineDurationMs(), length)
    }
    val tail = clip.transitionOut?.takeIf { index < clips.lastIndex && !it.type.overlapping }?.let { outgoing ->
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
    val keyframes = clip.keyframesOnTimeline()
    val visual = VisualSpec(
        look = clip.look.takeUnless { it.isIdentity },
        rotationDegrees = clip.rotationDegrees,
        mirrored = clip.mirrored,
        crop = clip.crop?.takeUnless { it.isFull },
        frame = frameSpecOf(clip, live, motion.time, overlay, keyframes),
        transition = motion.transition,
        chroma = clip.chroma,
        lookKeyframes = keyframes.takeIf { clip.animatesLook() }.orEmpty(),
        keyframesStartUs = if (clip.animatesLook()) motion.time.startUs else 0L,
        effect = clip.effect?.let { ClipEffectSpec(it, motion.time.startUs) },
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
                val silent = clip.muted || originalAudioMuted
                SoundSpec(
                    if (silent) 0f else clip.volume, fadeIn, fadeOut, length,
                    volumeKeyframes = keyframes.takeUnless { silent || keyframes.all { it.volume == clip.volume } }.orEmpty(),
                )
            },
            visual = visual,
        )
        ClipType.IMAGE -> ItemSpec.Image(uri = clip.uri, durationMs = clip.trimEndMs - clip.trimStartMs, visual = visual, rotationDegrees = clip.sourceRotationDegrees)
    }
}

/**
 * Null for a plainly fitted, opaque, still clip on black, unless the preview needs it [live].
 * Overlays always get one, on a transparent frame.
 */
private fun frameSpecOf(clip: Clip, live: Boolean, time: ClipTime, overlay: Boolean, keyframes: List<ClipKeyframe>): FrameSpec? {
    val animated = clip.animationIn != null || clip.animationOut != null || clip.animationCombo != null
    val plain = clip.fit == FrameFit.FIT && clip.transform.isIdentity && clip.opacity >= 1f && clip.canvas.isDefault && !animated &&
        clip.mask == null && clip.chroma == null && keyframes.isEmpty()
    if (plain && !live && !overlay) return null
    return FrameSpec(
        clip.fit, clip.transform, clip.opacity.coerceIn(0f, 1f), clip.canvas,
        liveKey = clip.id.takeIf { live },
        animation = if (animated) AnimationSpec(time, clip.animationIn, clip.animationOut, clip.animationCombo) else null,
        transparent = overlay,
        mask = clip.mask,
        keyframes = keyframes,
        clipStartUs = if (keyframes.isEmpty()) 0L else time.startUs,
    )
}

/** A text overlay ready to render in a frame of [outputSize] (also the editor's on-screen box). */
fun textSpecFor(overlay: TextOverlay, outputSize: OutputSize): TextSpec {
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
        keyframes = overlay.keyframes,
        textSizeSp = overlay.textSizeSp,
        style = overlay,
    )
}

/** Whether keyframes change the clip's grading (filter or Adjust) or chroma strength. */
private fun Clip.animatesLook(): Boolean {
    if (keyframes.isEmpty()) return false
    val first = keyframes.first()
    return keyframes.any { it.filterIntensity != first.filterIntensity || it.adjust != first.adjust || it.chromaIntensity != first.chromaIntensity || it.chromaShadow != first.chromaShadow } ||
        !first.adjust.isIdentity
}

/**
 * Whether the composition runs any of PreCut's own GL passes (placement, looks, masks, overlays,
 * transitions, layers, effects, texts, cover), which work in SDR only.
 */
fun CompositionSpec.usesOwnShaders(): Boolean =
    items.any { it.visual.let { v -> v.frame != null || v.look != null || v.chroma != null || v.effect != null || v.transition != null || v.crop != null } } ||
        overlayLanes.isNotEmpty() || transitions.isNotEmpty() || lookLayers.isNotEmpty() || effectLayers.isNotEmpty() ||
        texts.isNotEmpty() || cover != null

/** Whether a text overlay is on screen at composition time [presentationTimeUs]. */
fun TextSpec.isVisibleAt(presentationTimeUs: Long): Boolean = presentationTimeUs in startUs until endUs
