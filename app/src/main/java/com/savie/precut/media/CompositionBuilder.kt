package com.savie.precut.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.SpeedParameters
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.effect.FrameDropEffect
import com.savie.precut.model.DEFAULT_SHORT_SIDE_PX
import com.savie.precut.model.OutputSize
import com.savie.precut.model.Project
import com.savie.precut.model.speed.SpeedSteps
import javax.inject.Inject

/**
 * How an export differs from the preview: a lower frame rate (frames are dropped), keeping HDR,
 * or sound only. The preview passes none.
 */
data class ExportOutput(val frameRate: Int? = null, val keepHdr: Boolean = false, val audioOnly: Boolean = false)

/** The single Project → Composition path shared by preview and export. */
// Media3's composition API (sequences, gaps, speed, HDR modes) is unstable in 1.11 and is used throughout.
@OptIn(UnstableApi::class)
class CompositionBuilder @Inject constructor(private val effectsFactory: EffectsFactory) {

    /**
     * Returns null when the project has nothing to play. [shortSidePx] sets the output size;
     * [liveClipId] is for the preview only (see [compositionSpecOf]).
     */
    fun build(project: Project, shortSidePx: Int = DEFAULT_SHORT_SIDE_PX, liveClipId: String? = null, output: ExportOutput? = null): Composition? {
        val spec = compositionSpecOf(project, shortSidePx, liveClipId)
        if (spec.items.isEmpty()) return null
        if (output?.audioOnly == true) return buildAudioOnly(spec)
        val main = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
            .addItems(spec.items.map { toEditedMediaItem(it, spec.outputSize) })
            .build()
        val overlayAudio = (spec.overlayLanes + listOfNotNull(spec.transitionAudio)).mapNotNull(::overlayAudioSequence)
        val builder = Composition.Builder(listOf(main) + overlayAudio + spec.audioLanes.map { audioSequence(it, spec.durationUs) })
            // PreCut's own shaders work in SDR, so HDR stays HDR only when none of them run.
            .setHdrMode(
                if (output?.keepHdr == true && !spec.usesOwnShaders()) Composition.HDR_MODE_KEEP_HDR
                else Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
            )
        // Overlays go on the main video, layers grade the result, and text and the cover go on top, ungraded.
        // A lower frame rate drops frames first, so later passes do less work.
        val videoEffects = listOfNotNull(output?.frameRate?.let { FrameDropEffect.createDefaultFrameDropEffect(it.toFloat()) }) +
            listOfNotNull(effectsFactory.overlayLayersEffect(spec.overlayLanes, spec.transitions, spec.outputSize)) +
            effectsFactory.lookLayerEffects(spec.lookLayers) +
            effectsFactory.effectLayerEffects(spec.effectLayers) +
            listOfNotNull(effectsFactory.overlayEffect(spec.texts, spec.cover, spec.outputSize))
        if (videoEffects.isNotEmpty()) builder.setEffects(Effects(/* audioProcessors= */ emptyList(), videoEffects))
        return builder.build()
    }

    /** Every sound of the project, no picture (Audio only, EX-01): photos become silence. */
    private fun buildAudioOnly(spec: CompositionSpec): Composition {
        val main = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        spec.items.forEach { item ->
            when (item) {
                is ItemSpec.Video -> main.addItem(
                    EditedMediaItem.Builder(clippedItem(item.uri, item.clipStartMs, item.clipEndMs))
                        .setDurationUs(item.sourceDurationMs * 1_000)
                        .setRemoveVideo(true)
                        .setEffects(Effects(effectsFactory.audioProcessors(item.sound), emptyList()))
                        .apply { applyTiming(item, capFrameRate = false) }
                        .build(),
                )
                is ItemSpec.Image -> main.addGap(item.durationMs * 1_000)
            }
        }
        val overlayAudio = (spec.overlayLanes + listOfNotNull(spec.transitionAudio)).mapNotNull(::overlayAudioSequence)
        return Composition.Builder(listOf(main.build()) + overlayAudio + spec.audioLanes.map { audioSequence(it, spec.durationUs) }).build()
    }

    /**
     * An overlay row's sound as an audio-only sequence: silence up to each video overlay, then its
     * audio. Null when nothing in the row makes a sound. (Their pictures are drawn by
     * [EffectsFactory.overlayLayersEffect].)
     */
    private fun overlayAudioSequence(lane: OverlayLaneSpec): EditedMediaItemSequence? {
        if (lane.items.none { (it.item as? ItemSpec.Video)?.sound?.volume?.let { v -> v > 0f } == true }) return null
        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        var silenceUs = 0L
        lane.items.forEach { overlay ->
            silenceUs += overlay.gapBeforeUs
            val video = overlay.item as? ItemSpec.Video
            if (video == null || video.sound.volume <= 0f) {
                silenceUs += overlay.endUs - overlay.startUs
                return@forEach
            }
            if (silenceUs > 0) builder.addGap(silenceUs)
            silenceUs = 0L
            builder.addItem(
                EditedMediaItem.Builder(clippedItem(video.uri, video.clipStartMs, video.clipEndMs))
                    .setDurationUs(video.sourceDurationMs * 1_000)
                    .setEffects(Effects(effectsFactory.audioProcessors(video.sound), emptyList()))
                    .apply { applyTiming(video, capFrameRate = false) }
                    .build(),
            )
        }
        silenceUs += lane.trailingGapUs
        if (silenceUs > 0) builder.addGap(silenceUs)
        return builder.build()
    }

    /**
     * One audio row: for each clip, a silent gap up to its start, then the clip, then silence to
     * the video's end ([durationUs]). A row that ends early stalls `CompositionPlayer` (1.11.1):
     * after a seek back over its end, the mixer waits on the ended input and playback freezes.
     */
    private fun audioSequence(lane: List<AudioItemSpec>, durationUs: Long): EditedMediaItemSequence {
        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        var endUs = 0L
        lane.forEach { audio ->
            if (audio.gapBeforeMs > 0) builder.addGap(audio.gapBeforeMs * 1_000)
            endUs += audio.gapBeforeMs * 1_000 + ((audio.clipEndMs - audio.clipStartMs) * 1_000 / audio.speed).toLong()
            builder.addItem(
                EditedMediaItem.Builder(clippedItem(audio.uri, audio.clipStartMs, audio.clipEndMs))
                    .setDurationUs(audio.sourceDurationMs * 1_000)
                    .setEffects(Effects(effectsFactory.audioProcessors(audio.sound), emptyList()))
                    .apply { if (audio.speed != 1f) applySpeed(audio.speed, capFrameRate = false) }
                    .build(),
            )
        }
        if (durationUs - endUs >= MIN_TRAILING_GAP_US) builder.addGap(durationUs - endUs)
        return builder.build()
    }

    private fun toEditedMediaItem(item: ItemSpec, outputSize: OutputSize): EditedMediaItem {
        val audio = if (item is ItemSpec.Video) effectsFactory.audioProcessors(item.sound) else emptyList()
        val effects = Effects(audio, effectsFactory.videoEffects(item.visual, outputSize))
        return when (item) {
            is ItemSpec.Video ->
                EditedMediaItem.Builder(clippedItem(item.uri, item.clipStartMs, item.clipEndMs))
                    // CompositionPlayer requires the pre-clipping, pre-speed source duration.
                    .setDurationUs(item.sourceDurationMs * 1_000)
                    .setEffects(effects)
                    .apply { applyTiming(item) }
                    .build()
            is ItemSpec.Image -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(item.uri)
                    .setImageDurationMs(item.durationMs)
                    .build()
                EditedMediaItem.Builder(mediaItem)
                    .setDurationUs(item.durationMs * 1_000)
                    .setFrameRate(IMAGE_FRAME_RATE)
                    .setEffects(effects)
                    .build()
            }
        }
    }

    private fun clippedItem(uri: String, startMs: Long, endMs: Long): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build(),
        )
        .build()

    /** A video's speed or speed curve (laid over its curve range, which may start before this item). */
    private fun EditedMediaItem.Builder.applyTiming(item: ItemSpec.Video, capFrameRate: Boolean = true) {
        val steps = speedStepsOf(item)
        if (steps != null) {
            val offsetUs = (item.clipStartMs - (item.curveRangeMs?.first ?: item.clipStartMs)) * 1_000
            applyCurve(steps, item.keepPitch, offsetUs)
        } else if (item.speed != 1f) {
            applySpeed(item.speed, capFrameRate = capFrameRate, keepPitch = item.keepPitch)
        }
    }

    /**
     * Constant speed for preview and export alike, optionally keeping the audio's pitch. Sped-up clips get a
     * frame-rate cap so 4x footage doesn't produce a 120+ fps output.
     */
    @OptIn(ExperimentalApi::class) // SpeedParameters (pitch control) is experimental in Media3 1.11.
    private fun EditedMediaItem.Builder.applySpeed(speed: Float, capFrameRate: Boolean = true, keepPitch: Boolean = true) {
        setSpeed(SpeedParameters(ConstantSpeedProvider(speed), /* shouldMaintainPitch= */ keepPitch))
        if (capFrameRate && speed > 1f) setFrameRate(MAX_FRAME_RATE)
    }

    /** A speed curve's steps, with the same frame-rate cap as a sped-up clip where it goes above 1x. */
    @OptIn(ExperimentalApi::class) // SpeedParameters (pitch control) is experimental in Media3 1.11.
    private fun EditedMediaItem.Builder.applyCurve(steps: SpeedSteps, keepPitch: Boolean, offsetUs: Long) {
        setSpeed(SpeedParameters(CurveSpeedProvider(steps, offsetUs), /* shouldMaintainPitch= */ keepPitch))
        if (steps.maxSpeed() > 1f) setFrameRate(MAX_FRAME_RATE)
    }

    private companion object {
        const val IMAGE_FRAME_RATE = 30
        const val MAX_FRAME_RATE = 60
        /** Shorter trailing silence isn't worth a gap item. */
        const val MIN_TRAILING_GAP_US = 1_000L
    }
}
