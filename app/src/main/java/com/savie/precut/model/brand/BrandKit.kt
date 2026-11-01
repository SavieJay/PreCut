package com.savie.precut.model.brand

import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.sticker.Sticker
import com.savie.precut.model.sticker.StickerKind
import com.savie.precut.model.totalDurationMs
import kotlinx.serialization.Serializable

/** A picture or video saved in the brand kit, with what's needed to make a clip of it. */
@Serializable
data class BrandMedia(
    val uri: String,
    val type: ClipType,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
) {
    val info: MediaInfo get() = MediaInfo(type, durationMs, width, height, rotationDegrees)

    /** Width ÷ height as shown. */
    val aspect: Float
        get() = if (width <= 0 || height <= 0) 1f else if (rotationDegrees % 180 == 0) width.toFloat() / height else height.toFloat() / width
}

/**
 * The brand kit (Phase 26B, Pro): saved once in Settings, applied to a project in one tap (and to
 * new projects and templates automatically when [applyToNew]).
 */
@Serializable
data class BrandKit(
    val logo: BrandMedia? = null,
    /** Up to five colours; texts take the first. */
    val colors: List<Int> = emptyList(),
    /** `BundledFont` ids: titles and captions. */
    val titleFontId: String? = null,
    val bodyFontId: String? = null,
    val intro: BrandMedia? = null,
    val outro: BrandMedia? = null,
    val applyToNew: Boolean = false,
) {
    val isEmpty: Boolean get() = logo == null && colors.isEmpty() && titleFontId == null && bodyFontId == null && intro == null && outro == null
}

/** Ids of what the brand kit adds start with these, so applying it again replaces them. */
const val BRAND_INTRO = "brand-intro"
const val BRAND_OUTRO = "brand-outro"
const val BRAND_LOGO = "brand-logo"

const val MAX_BRAND_COLORS = 5

/** Whether the brand kit was applied to this project. */
fun Project.isBranded(): Boolean =
    clips.any { it.id.startsWith(BRAND_INTRO) || it.id.startsWith(BRAND_OUTRO) } || textOverlays.any { it.id.startsWith(BRAND_LOGO) }

/**
 * [kit] on this project: the intro first and the outro last (replacing ones added before), the
 * logo small in the top corner for the whole video, and the kit's first colour and fonts on the
 * texts (titles take the title font, captions the body font). [suffix] makes the new ids unique.
 */
fun Project.withBrandKit(kit: BrandKit, suffix: String, photoDurationMs: Long): Project {
    val kept = clips.filterNot { it.id.startsWith(BRAND_INTRO) || it.id.startsWith(BRAND_OUTRO) }
    if (kept.isEmpty()) return this
    val intro = kit.intro?.let { clipFromMedia("$BRAND_INTRO-$suffix", it.uri, it.info, photoDurationMs) }
    val outro = kit.outro?.let { clipFromMedia("$BRAND_OUTRO-$suffix", it.uri, it.info, photoDurationMs) }
    val withClips = copy(clips = listOfNotNull(intro) + kept + listOfNotNull(outro))
    // Texts and the rest keep their moments in the content: a new (or replaced) intro moves them.
    val firstContent = kept.first()
    val shift = withClips.clipStartTimesMs()[withClips.clips.indexOf(firstContent)] - clipStartTimesMs()[clips.indexOf(firstContent)]
    val color = kit.colors.firstOrNull()
    val texts = textOverlays.filterNot { it.id.startsWith(BRAND_LOGO) }.map { text ->
        val moved = text.copy(startMs = text.startMs + shift, endMs = text.endMs + shift)
        when {
            text.sticker != null -> moved
            text.caption != null -> moved.copy(fontId = kit.bodyFontId ?: kit.titleFontId ?: text.fontId)
            else -> moved.copy(fontId = kit.titleFontId ?: text.fontId, colorArgb = color ?: text.colorArgb)
        }
    }
    val total = withClips.totalDurationMs()
    val logo = kit.logo?.let {
        TextOverlay(
            id = "$BRAND_LOGO-$suffix", text = "", startMs = 0L, endMs = total,
            xNorm = 0.86f, yNorm = 0.08f, textSizeSp = 28f, opacity = 0.9f,
            sticker = Sticker(StickerKind.IMAGE, it.uri, it.aspect),
        )
    }
    return withClips.copy(
        textOverlays = texts + listOfNotNull(logo),
        audioClips = audioClips.map { it.copy(timelineStartMs = it.timelineStartMs + shift) },
        overlays = overlays.map { it.copy(timelineStartMs = it.timelineStartMs + shift) },
        effectLayers = effectLayers.map { it.copy(startMs = it.startMs + shift, endMs = it.endMs + shift) },
        lookLayers = lookLayers.map { it.copy(startMs = it.startMs + shift, endMs = it.endMs + shift) },
    )
}
