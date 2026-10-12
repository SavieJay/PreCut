package com.savie.precut.media

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import com.savie.precut.model.Project
import javax.inject.Inject

/** The single Project → Composition path shared by preview and export. */
class CompositionBuilder @Inject constructor() {

    /** Returns null when the project has nothing to play. */
    fun build(project: Project): Composition? {
        val spec = compositionSpecOf(project)
        if (spec.items.isEmpty()) return null
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
            .addItems(spec.items.map(::toEditedMediaItem))
            .build()
        return Composition.Builder(sequence).build()
    }

    private fun toEditedMediaItem(item: ItemSpec): EditedMediaItem = when (item) {
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
                .build()
        }
    }

    private companion object {
        const val IMAGE_FRAME_RATE = 30
    }
}
