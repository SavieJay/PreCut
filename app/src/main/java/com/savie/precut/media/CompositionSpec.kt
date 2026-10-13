package com.savie.precut.media

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.OutputSize
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.CoverSource
import com.savie.precut.model.DEFAULT_SHORT_SIDE_PX
import com.savie.precut.model.coverSource
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.outputSize
import com.savie.precut.model.audioLanes
import com.savie.precut.model.edit.clampFades
import com.savie.precut.model.placementWithin
import com.savie.precut.model.timelineDurationMs
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
)

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

/** Look shared by every item: grading, then user rotation, then fit into the frame. */
data class VisualSpec(
    /** Null when the clip isn't graded. */
    val look: ClipLook? = null,
    /** Clockwise, 0/90/180/270. */
    val rotationDegrees: Int = 0,
)

sealed interface ItemSpec {
    val visual: VisualSpec

    data class Video(
        val uri: String,
        val sourceDurationMs: Long,
        val clipStartMs: Long,
        val clipEndMs: Long,
        val speed: Float = 1f,
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

/** The composition for [project] with an output short side of [shortSidePx] (preview renders smaller). */
fun compositionSpecOf(project: Project, shortSidePx: Int = DEFAULT_SHORT_SIDE_PX): CompositionSpec {
    val outputSize = project.outputSize(shortSidePx)
    return CompositionSpec(
        items = project.clips.map { itemSpecOf(it, project.originalAudioMuted) },
        outputSize = outputSize,
        texts = project.textOverlays.filter { it.text.isNotBlank() }.map { textSpecOf(it, outputSize) },
        audioLanes = audioLanesOf(project),
        cover = project.coverSource()?.takeIf { project.coverInExport && project.cover != null }
            ?.let { CoverSpec(it, COVER_DURATION_US) },
        lookLayers = project.lookLayers.filterNot { it.look.isIdentity }
            .map { LookLayerSpec(it.startMs * 1_000, it.endMs * 1_000, it.look) },
    )
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

private fun itemSpecOf(clip: Clip, originalAudioMuted: Boolean): ItemSpec {
    val visual = VisualSpec(clip.look.takeUnless { it.isIdentity }, clip.rotationDegrees)
    return when (clip.type) {
        ClipType.VIDEO -> ItemSpec.Video(
            uri = clip.uri,
            sourceDurationMs = clip.sourceDurationMs,
            clipStartMs = clip.trimStartMs,
            clipEndMs = clip.trimEndMs,
            speed = clip.speed,
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
