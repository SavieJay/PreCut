package com.savie.precut.model.text

import kotlinx.serialization.Serializable

/**
 * Text styling (X-04). Lengths (stroke width, blur, distance, spread, padding, corners) are in the
 * same units as text size: sp on a 360 dp-wide frame, scaled to the output like the text.
 */

@Serializable
enum class TextAlign { LEFT, CENTER, RIGHT }

/** Bundled fonts (OFL); null in [com.savie.precut.model.TextOverlay.fontId] is Roboto Flex. */
@Serializable
enum class BundledFont(val id: String, val displayName: String) {
    BEBAS_NEUE("bebas_neue", "Bebas Neue"),
    PLAYFAIR("playfair_display", "Playfair Display"),
    PACIFICO("pacifico", "Pacifico"),
    SPACE_MONO("space_mono", "Space Mono"),
    CAVEAT("caveat", "Caveat"),
    ANTON("anton", "Anton"),
    LOBSTER("lobster", "Lobster"),
    ;

    companion object {
        /** An imported font's id: its file name in app storage. */
        const val IMPORTED_PREFIX = "file:"

        fun of(id: String?): BundledFont? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
data class TextStroke(val colorArgb: Int = BLACK, val width: Float = 3f)

@Serializable
data class TextShadow(val colorArgb: Int = BLACK, val blur: Float = 6f, val distance: Float = 5f, val angleDegrees: Float = 135f)

/** A soft halo: [strength] 0..1, [spread] how far it reaches. */
@Serializable
data class TextGlow(val colorArgb: Int = PINK, val strength: Float = 0.7f, val spread: Float = 12f)

/** Text animations (X-04d): In and Out play at the ends, Loop for the whole text. */
@Serializable
enum class TextAnimationType { TYPEWRITER, FADE, SLIDE, POP, BOUNCE, WAVE }

const val MIN_TEXT_ANIMATION_MS = 200L
const val MAX_TEXT_ANIMATION_MS = 5_000L

@Serializable
data class TextAnimation(val type: TextAnimationType, val durationMs: Long = 1_200L)

/**
 * Rich text (X-05): characters [start] until [end] (of the text) drawn with their own colour,
 * size and style; null keeps the text's own.
 */
@Serializable
data class TextSpan(
    val start: Int,
    val end: Int,
    val colorArgb: Int? = null,
    val sizeSp: Float? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
)

/** Curve amount: -100 (arch down) .. 100 (arch up); 0 is straight. */
const val MAX_TEXT_CURVE = 100f

const val BLACK = 0xFF000000.toInt()
const val WHITE = 0xFFFFFFFF.toInt()
const val PINK = 0xFFFF4D6D.toInt()
