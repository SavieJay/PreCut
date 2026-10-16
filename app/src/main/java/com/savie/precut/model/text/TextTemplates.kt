package com.savie.precut.model.text

import com.savie.precut.model.TextOverlay
import com.savie.precut.model.withStyleOf

/** One-tap text looks (X-04e): a style plus an animation, shown on [sample] words. */
data class TextTemplate(val id: String, val sample: String, val style: TextOverlay) {
    fun applyTo(text: TextOverlay): TextOverlay = text.withStyleOf(style)
}

object TextTemplates {
    private fun look(build: TextOverlay.() -> TextOverlay): TextOverlay = TextOverlay("", "", 0L, 0L).build()

    val all: List<TextTemplate> = listOf(
        TextTemplate("headline", "HEADLINE", look {
            copy(fontId = BundledFont.ANTON.id, textSizeSp = 44f, stroke = TextStroke(BLACK, 3f), animationIn = TextAnimation(TextAnimationType.POP, 600L))
        }),
        TextTemplate("label", "Label", look {
            copy(textSizeSp = 24f, colorArgb = BLACK, backgroundArgb = WHITE, backgroundCorners = 12f, backgroundPadding = 10f, bold = true)
        }),
        TextTemplate("vibes", "Vibes", look {
            copy(fontId = BundledFont.PACIFICO.id, textSizeSp = 40f, colorArgb = 0xFFFFE066.toInt(), glow = TextGlow(PINK, 0.8f, 14f), animationLoop = TextAnimation(TextAnimationType.WAVE, 2_000L))
        }),
        TextTemplate("chapter", "Chapter one", look {
            copy(fontId = BundledFont.PLAYFAIR.id, textSizeSp = 34f, italic = true, letterSpacing = 4f, animationIn = TextAnimation(TextAnimationType.FADE, 1_200L))
        }),
        TextTemplate("day", "day 3 ✓", look {
            copy(fontId = BundledFont.CAVEAT.id, textSizeSp = 40f, colorArgb = WHITE, shadow = TextShadow(BLACK, 6f, 4f, 135f), animationIn = TextAnimation(TextAnimationType.TYPEWRITER, 1_000L))
        }),
        TextTemplate("subscribe", "SUBSCRIBE", look {
            copy(textSizeSp = 26f, bold = true, colorArgb = WHITE, backgroundArgb = 0xFFD92B50.toInt(), backgroundCorners = 20f, backgroundPadding = 12f, animationLoop = TextAnimation(TextAnimationType.BOUNCE, 1_500L))
        }),
        TextTemplate("city", "LAGOS", look {
            copy(fontId = BundledFont.BEBAS_NEUE.id, textSizeSp = 64f, letterSpacing = 12f, colorArgb = WHITE, shadow = TextShadow(BLACK, 10f, 0f, 0f), animationIn = TextAnimation(TextAnimationType.SLIDE, 800L))
        }),
        TextTemplate("retro", "RETRO", look {
            copy(fontId = BundledFont.LOBSTER.id, textSizeSp = 48f, colorArgb = 0xFFFF9F1C.toInt(), stroke = TextStroke(0xFF2EC4B6.toInt(), 4f), curve = 40f)
        }),
        TextTemplate("lower_third", "Lower third", look {
            copy(textSizeSp = 22f, align = TextAlign.LEFT, colorArgb = WHITE, backgroundArgb = BLACK, backgroundOpacity = 0.7f, backgroundCorners = 0f, backgroundPadding = 14f, animationIn = TextAnimation(TextAnimationType.SLIDE, 600L))
        }),
    )
}
