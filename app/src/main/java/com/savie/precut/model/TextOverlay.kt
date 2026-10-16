package com.savie.precut.model

import com.savie.precut.model.keyframe.TextKeyframe
import com.savie.precut.model.sticker.Sticker
import com.savie.precut.model.text.TextAlign
import com.savie.precut.model.text.TextAnimation
import com.savie.precut.model.text.TextGlow
import com.savie.precut.model.text.TextShadow
import com.savie.precut.model.text.TextSpan
import com.savie.precut.model.text.TextStroke
import kotlinx.serialization.Serializable

@Serializable
data class TextOverlay(
    val id: String,
    val text: String,
    /** Timeline time. */
    val startMs: Long,
    val endMs: Long,
    /** 0..1 position in frame. */
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.8f,
    val textSizeSp: Float = 32f,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundArgb: Int? = null,
    /** Clockwise, any angle. */
    val rotationDegrees: Float = 0f,
    /** 0..1, applied to the text and its background. */
    val opacity: Float = 1f,
    /** Animated place, size, turn and opacity (P-05), sorted by [TextKeyframe.atMs] (time into the text). */
    val keyframes: List<TextKeyframe> = emptyList(),
    // Styling (X-04); every default draws the text as before.
    /** A [BundledFont] id or an imported font (`file:<name>`); null is Roboto Flex. */
    val fontId: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val align: TextAlign = TextAlign.CENTER,
    /** Extra space between letters, -5..20 (tenths of an em / 4). */
    val letterSpacing: Float = 0f,
    /** Line height multiplier, 0.8..2. */
    val lineSpacing: Float = 1f,
    val stroke: TextStroke? = null,
    val shadow: TextShadow? = null,
    val glow: TextGlow? = null,
    /** The background's opacity, corner radius and padding; null corners/padding keep the classic proportions. */
    val backgroundOpacity: Float = 1f,
    val backgroundCorners: Float? = null,
    val backgroundPadding: Float? = null,
    /** -100 (arch down) .. 100 (arch up); 0 is straight. */
    val curve: Float = 0f,
    val animationIn: TextAnimation? = null,
    val animationOut: TextAnimation? = null,
    val animationLoop: TextAnimation? = null,
    /** Rich text (X-05): ranges with their own colour, size or style. */
    val spans: List<TextSpan> = emptyList(),
    /** Set on a sticker (S-01): what it shows instead of words. */
    val sticker: Sticker? = null,
)

/** This text with [other]'s look (font, colours, effects, animations); its words, timing and place stay. */
fun TextOverlay.withStyleOf(other: TextOverlay): TextOverlay = copy(
    textSizeSp = other.textSizeSp,
    colorArgb = other.colorArgb,
    backgroundArgb = other.backgroundArgb,
    opacity = other.opacity,
    fontId = other.fontId,
    bold = other.bold,
    italic = other.italic,
    underline = other.underline,
    align = other.align,
    letterSpacing = other.letterSpacing,
    lineSpacing = other.lineSpacing,
    stroke = other.stroke,
    shadow = other.shadow,
    glow = other.glow,
    backgroundOpacity = other.backgroundOpacity,
    backgroundCorners = other.backgroundCorners,
    backgroundPadding = other.backgroundPadding,
    curve = other.curve,
    animationIn = other.animationIn,
    animationOut = other.animationOut,
    animationLoop = other.animationLoop,
)

/** [spans] kept inside the text, empty ones dropped (after the words change). */
fun TextOverlay.validSpans(): List<TextSpan> =
    spans.mapNotNull { span ->
        val start = span.start.coerceIn(0, text.length)
        val end = span.end.coerceIn(start, text.length)
        if (end > start) span.copy(start = start, end = end) else null
    }

/**
 * [style] applied to characters [start] until [end] (X-05), on top of what spans already say;
 * ranges split where they partly overlap.
 */
fun TextOverlay.withSpanStyle(start: Int, end: Int, style: TextSpan): TextOverlay {
    if (end <= start) return this
    val pieces = mutableListOf<TextSpan>()
    validSpans().forEach { span ->
        if (span.end <= start || span.start >= end) {
            pieces += span
        } else {
            if (span.start < start) pieces += span.copy(end = start)
            if (span.end > end) pieces += span.copy(start = end)
            pieces += span.copy(start = maxOf(span.start, start), end = minOf(span.end, end)).merged(style)
        }
    }
    // Parts of the range no span covered yet.
    var cursor = start
    validSpans().filter { it.end > start && it.start < end }.sortedBy { it.start }.forEach { span ->
        if (span.start > cursor) pieces += style.copy(start = cursor, end = span.start)
        cursor = maxOf(cursor, span.end)
    }
    if (cursor < end) pieces += style.copy(start = cursor, end = end)
    return copy(spans = pieces.filter { it.end > it.start }.sortedBy { it.start })
}

private fun TextSpan.merged(style: TextSpan) = copy(
    colorArgb = style.colorArgb ?: colorArgb,
    sizeSp = style.sizeSp ?: sizeSp,
    bold = style.bold ?: bold,
    italic = style.italic ?: italic,
    underline = style.underline ?: underline,
)

/** Text size bounds for the size slider and pinch-to-scale. */
const val MIN_TEXT_SIZE_SP = 8f
const val MAX_TEXT_SIZE_SP = 160f
