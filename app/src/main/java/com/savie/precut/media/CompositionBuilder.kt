package com.savie.precut.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.SpeedParameters
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.savie.precut.model.DEFAULT_SHORT_SIDE_PX
import com.savie.precut.model.OutputSize
import com.savie.precut.model.Project
import com.savie.precut.model.speed.SpeedSteps
import javax.inject.Inject

/** The single Project → Composition path shared by preview and export. */
class CompositionBuilder @Inject constructor(private val effectsFactory: EffectsFactory) {

    /**
     * Returns null when the project has nothing to play. [shortSidePx] sets the output size;
     * [liveClipId] is for the preview only (see [compositionSpecOf]).
     */
    fun build(project: Project, shortSidePx: Int = DEFAULT_SHORT_SIDE_PX, liveClipId: String? = null): Composition? {
        val spec = compositionSpecOf(project, shortSidePx, liveClipId)
        if (spec.items.isEmpty()) return null
        val main = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
            .addItems(spec.items.map { toEditedMediaItem(it, spec.outputSize) })
            .build()
        val layers = videoLayers(spec, main)
        val builder = Composition.Builder(layers.sequences + spec.audioLanes.map(::audioSequence))
            // The look shader works in SDR; HDR sources are tone-mapped (HDR export: Phase 12).
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        layers.compositor?.let(builder::setVideoCompositorSettings)
        // Layers grade the composed frame; text and the cover go on top, ungraded.
        val videoEffects = effectsFactory.lookLayerEffects(spec.lookLayers) +
            listOfNotNull(effectsFactory.overlayEffect(spec.texts, spec.cover, spec.outputSize))
        if (videoEffects.isNotEmpty()) builder.setEffects(Effects(/* audioProcessors= */ emptyList(), videoEffects))
        return builder.build()
    }

    private class VideoLayers(val sequences: List<EditedMediaItemSequence>, val compositor: LayerCompositorSettings?)

    /**
     * The video sequences, top first. Without overlays, just [main]. With them, Media3's compositor
     * draws the first sequence on top and takes its frame timing from it, so the order is: a
     * gap-only timing track (invisible, steady 30 fps, the video's full length), the overlay rows
     * (top row first), then the main track.
     */
    private fun videoLayers(spec: CompositionSpec, main: EditedMediaItemSequence): VideoLayers {
        if (spec.overlayLanes.isEmpty()) return VideoLayers(listOf(main), null)
        val timing = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addGap(spec.durationUs).build()
        val rows = spec.overlayLanes.asReversed()
        val sequences = listOf(timing) + rows.map { overlaySequence(it, spec.outputSize) } + main
        val visible = listOf(emptyList<LongRange>()) + rows.map { row -> row.items.map { it.startUs until it.endUs } } + listOf(null)
        return VideoLayers(sequences, LayerCompositorSettings(spec.outputSize, visible))
    }

    /** One overlay row: a gap up to each overlay, the overlay, then a gap to the video's end. */
    private fun overlaySequence(lane: OverlayLaneSpec, outputSize: OutputSize): EditedMediaItemSequence {
        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
        lane.items.forEach { overlay ->
            if (overlay.gapBeforeUs > 0) builder.addGap(overlay.gapBeforeUs)
            builder.addItem(toEditedMediaItem(overlay.item, outputSize))
        }
        if (lane.trailingGapUs > 0) builder.addGap(lane.trailingGapUs)
        return builder.build()
    }

    /** One audio row: for each clip, a silent gap up to its start, then the clip. */
    private fun audioSequence(lane: List<AudioItemSpec>): EditedMediaItemSequence {
        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        lane.forEach { audio ->
            if (audio.gapBeforeMs > 0) builder.addGap(audio.gapBeforeMs * 1_000)
            val mediaItem = MediaItem.Builder()
                .setUri(audio.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(audio.clipStartMs)
                        .setEndPositionMs(audio.clipEndMs)
                        .build(),
                )
                .build()
            builder.addItem(
                EditedMediaItem.Builder(mediaItem)
                    .setDurationUs(audio.sourceDurationMs * 1_000)
                    .setEffects(Effects(effectsFactory.audioProcessors(audio.sound), emptyList()))
                    .apply { if (audio.speed != 1f) applySpeed(audio.speed, capFrameRate = false) }
                    .build(),
            )
        }
        return builder.build()
    }

    private fun toEditedMediaItem(item: ItemSpec, outputSize: OutputSize): EditedMediaItem {
        val audio = if (item is ItemSpec.Video) effectsFactory.audioProcessors(item.sound) else emptyList()
        val effects = Effects(audio, effectsFactory.videoEffects(item.visual, outputSize))
        return when (item) {
            is ItemSpec.Video -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(item.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(item.clipStartMs)
                            .setEndPositionMs(item.clipEndMs)
                            .build(),
                    )
                    .build()
                EditedMediaItem.Builder(mediaItem)
                    // CompositionPlayer requires the pre-clipping, pre-speed source duration.
                    .setDurationUs(item.sourceDurationMs * 1_000)
                    .setEffects(effects)
                    .apply {
                        val curve = item.speedCurve
                        if (curve != null) {
                            applyCurve(SpeedSteps.of(curve, (item.clipEndMs - item.clipStartMs) * 1_000), item.keepPitch)
                        } else if (item.speed != 1f) {
                            applySpeed(item.speed, keepPitch = item.keepPitch)
                        }
                    }
                    .build()
            }
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
    private fun EditedMediaItem.Builder.applyCurve(steps: SpeedSteps, keepPitch: Boolean) {
        setSpeed(SpeedParameters(CurveSpeedProvider(steps), /* shouldMaintainPitch= */ keepPitch))
        if (steps.maxSpeed() > 1f) setFrameRate(MAX_FRAME_RATE)
    }

    private companion object {
        const val IMAGE_FRAME_RATE = 30
        const val MAX_FRAME_RATE = 60
    }
}
