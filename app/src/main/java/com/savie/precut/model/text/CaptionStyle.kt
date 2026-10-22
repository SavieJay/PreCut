package com.savie.precut.model.text

import com.savie.precut.model.TextOverlay

private const val HIGHLIGHT_YELLOW = 0xFFFFD43B.toInt()

/**
 * Caption style presets (Phase 21). [id] is saved in [CaptionInfo.styleId], so it never changes
 * once shipped. A preset sets the look only; words, timing and place stay.
 */
enum class CaptionStyle(val id: String, val highlightArgb: Int?) {
    CLASSIC("classic", null),
    BOX("box", null),
    BOLD("bold", null),
    HIGHLIGHT("highlight", HIGHLIGHT_YELLOW),
    POP("pop", PINK),
    MINIMAL("minimal", null),
    ;

    /** [text] in this style, marked as styled by it. */
    fun applyTo(text: TextOverlay): TextOverlay {
        val plain = text.copy(
            textSizeSp = 26f, colorArgb = WHITE, backgroundArgb = null, backgroundOpacity = 1f,
            fontId = null, bold = false, italic = false, underline = false, align = TextAlign.CENTER,
            stroke = null, shadow = null, glow = null, curve = 0f,
            animationIn = null, animationOut = null, animationLoop = null, spans = emptyList(),
        )
        val styled = when (this) {
            CLASSIC -> plain.copy(stroke = TextStroke(BLACK, 3f))
            BOX -> plain.copy(backgroundArgb = BLACK, backgroundOpacity = 0.7f)
            BOLD -> plain.copy(textSizeSp = 30f, fontId = BundledFont.ANTON.id, stroke = TextStroke(BLACK, 4f))
            HIGHLIGHT -> plain.copy(textSizeSp = 28f, bold = true, stroke = TextStroke(BLACK, 4f))
            POP -> plain.copy(
                textSizeSp = 30f, fontId = BundledFont.POPPINS.id, bold = true, stroke = TextStroke(BLACK, 4f),
                animationIn = TextAnimation(TextAnimationType.POP, 250L),
            )
            MINIMAL -> plain.copy(textSizeSp = 22f, shadow = TextShadow(BLACK, 6f, 2f))
        }
        return styled.copy(caption = text.caption?.copy(styleId = id, highlightArgb = highlightArgb))
    }

    companion object {
        val DEFAULT = HIGHLIGHT
        fun byId(id: String?): CaptionStyle? = entries.firstOrNull { it.id == id }
    }
}
