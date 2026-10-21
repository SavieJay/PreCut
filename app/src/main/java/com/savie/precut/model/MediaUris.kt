package com.savie.precut.model

import com.savie.precut.model.sticker.StickerKind

/**
 * Every media Uri the project points at: clips and overlays (and a reversed clip's original),
 * audio, a picture cover, canvas pictures and picture stickers. One list for the media sweep,
 * templates and anything else that must find or move a project's files.
 */
fun Project.mediaUris(): List<String> {
    val all = clips + overlays.map { it.clip }
    return all.map { it.uri } + audioClips.map { it.uri } +
        listOfNotNull((cover as? ProjectCover.Image)?.uri) +
        all.mapNotNull { it.canvas.imageUri } +
        all.mapNotNull { it.reversedFrom?.uri } +
        textOverlays.mapNotNull { overlay -> overlay.sticker?.takeIf { it.kind != StickerKind.EMOJI }?.value }
}

/** The project with every Uri in [mediaUris] passed through [transform] (the same places, in the same way). */
fun Project.mapMediaUris(transform: (String) -> String): Project {
    fun Clip.mapped() = copy(
        uri = transform(uri),
        canvas = canvas.imageUri?.let { canvas.copy(imageUri = transform(it)) } ?: canvas,
        reversedFrom = reversedFrom?.let { it.copy(uri = transform(it.uri)) },
    )
    return copy(
        clips = clips.map { it.mapped() },
        overlays = overlays.map { it.copy(clip = it.clip.mapped()) },
        audioClips = audioClips.map { it.copy(uri = transform(it.uri)) },
        cover = (cover as? ProjectCover.Image)?.let { it.copy(uri = transform(it.uri)) } ?: cover,
        textOverlays = textOverlays.map { overlay ->
            val sticker = overlay.sticker?.takeIf { it.kind != StickerKind.EMOJI }
            if (sticker == null) overlay else overlay.copy(sticker = sticker.copy(value = transform(sticker.value)))
        },
    )
}
