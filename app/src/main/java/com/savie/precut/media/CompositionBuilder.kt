package com.savie.precut.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.savie.precut.model.OutputSize
import com.savie.precut.model.Project
import javax.inject.Inject

/** The single Project → Composition path shared by preview and export. */
class CompositionBuilder @Inject constructor() {

    /** Returns null when the project has nothing to play. */
    fun build(project: Project): Composition? {
        val spec = compositionSpecOf(project)
        if (spec.items.isEmpty()) return null
        val effects = frameEffects(spec.outputSize)
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
            .addItems(spec.items.map { toEditedMediaItem(it, effects) })
            .build()
        return Composition.Builder(sequence).build()
    }

    /**
     * Every item is fitted (letterboxed) into the same output frame, so mixed portrait/landscape
     * clips and full-resolution photos produce a consistent, encodable video.
     */
    @OptIn(UnstableApi::class)
    private fun frameEffects(size: OutputSize): Effects = Effects(
        /* audioProcessors= */ emptyList(),
        /* videoEffects= */ listOf(
            Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_SCALE_TO_FIT),
        ),
    )

    private fun toEditedMediaItem(item: ItemSpec, effects: Effects): EditedMediaItem = when (item) {
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
                // CompositionPlayer requires the pre-clipping source duration.
                .setDurationUs(item.sourceDurationMs * 1_000)
                .setEffects(effects)
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

    private companion object {
        const val IMAGE_FRAME_RATE = 30
    }
}
