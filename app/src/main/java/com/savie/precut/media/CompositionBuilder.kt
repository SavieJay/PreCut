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
import com.savie.precut.model.OutputSize
import com.savie.precut.model.Project
import javax.inject.Inject

/** The single Project → Composition path shared by preview and export. */
class CompositionBuilder @Inject constructor(private val effectsFactory: EffectsFactory) {

    /** Returns null when the project has nothing to play. */
    fun build(project: Project): Composition? {
        val spec = compositionSpecOf(project)
        if (spec.items.isEmpty()) return null
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
            .addItems(spec.items.map { toEditedMediaItem(it, spec.outputSize) })
            .build()
        val builder = Composition.Builder(sequence)
        effectsFactory.textOverlayEffect(spec.texts, spec.outputSize)?.let { overlay ->
            builder.setEffects(Effects(/* audioProcessors= */ emptyList(), /* videoEffects= */ listOf(overlay)))
        }
        return builder.build()
    }

    private fun toEditedMediaItem(item: ItemSpec, outputSize: OutputSize): EditedMediaItem {
        val effects = Effects(emptyList(), effectsFactory.videoEffects(item.visual, outputSize))
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
                    .apply { if (item.speed != 1f) applySpeed(item.speed) }
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
     * Constant speed for preview and export alike, keeping the audio's pitch. Sped-up clips get a
     * frame-rate cap so 4x footage doesn't produce a 120+ fps output.
     */
    @OptIn(ExperimentalApi::class) // SpeedParameters (pitch control) is experimental in Media3 1.11.
    private fun EditedMediaItem.Builder.applySpeed(speed: Float) {
        setSpeed(SpeedParameters(ConstantSpeedProvider(speed), /* shouldMaintainPitch= */ true))
        if (speed > 1f) setFrameRate(MAX_FRAME_RATE)
    }

    private companion object {
        const val IMAGE_FRAME_RATE = 30
        const val MAX_FRAME_RATE = 60
    }
}
