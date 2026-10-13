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
import javax.inject.Inject

/** The single Project → Composition path shared by preview and export. */
class CompositionBuilder @Inject constructor(private val effectsFactory: EffectsFactory) {

    /** Returns null when the project has nothing to play. [shortSidePx] sets the output size. */
    fun build(project: Project, shortSidePx: Int = DEFAULT_SHORT_SIDE_PX): Composition? {
        val spec = compositionSpecOf(project, shortSidePx)
        if (spec.items.isEmpty()) return null
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
            .addItems(spec.items.map { toEditedMediaItem(it, spec.outputSize) })
            .build()
        val builder = Composition.Builder(listOf(sequence) + spec.audioLanes.map(::audioSequence))
        effectsFactory.overlayEffect(spec.texts, spec.cover, spec.outputSize)?.let { overlay ->
            builder.setEffects(Effects(/* audioProcessors= */ emptyList(), /* videoEffects= */ listOf(overlay)))
        }
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
                    .apply { if (item.speed != 1f) applySpeed(item.speed, keepPitch = item.keepPitch) }
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

    private companion object {
        const val IMAGE_FRAME_RATE = 30
        const val MAX_FRAME_RATE = 60
    }
}
