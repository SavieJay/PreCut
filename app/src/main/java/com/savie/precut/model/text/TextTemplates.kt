package com.savie.precut.model.text

import com.savie.precut.model.TextOverlay
import com.savie.precut.model.withStyleOf

/** The Templates tab's groups. */
enum class TextTemplateCategory { TITLE, SOCIAL, CAPTION, SCRIPT, FUN }

/** One-tap text looks (X-04e): a style plus an animation, shown on [sample] words. */
data class TextTemplate(val id: String, val category: TextTemplateCategory, val sample: String, val style: TextOverlay) {
    fun applyTo(text: TextOverlay): TextOverlay = text.withStyleOf(style)
}

/** The built-in text templates; Phase 19 added the ones in the bundled fonts it brought. */
object TextTemplates {
    private fun look(build: TextOverlay.() -> TextOverlay): TextOverlay = TextOverlay("", "", 0L, 0L).build()

    private fun title(id: String, sample: String, build: TextOverlay.() -> TextOverlay) = TextTemplate(id, TextTemplateCategory.TITLE, sample, look(build))
    private fun social(id: String, sample: String, build: TextOverlay.() -> TextOverlay) = TextTemplate(id, TextTemplateCategory.SOCIAL, sample, look(build))
    private fun caption(id: String, sample: String, build: TextOverlay.() -> TextOverlay) = TextTemplate(id, TextTemplateCategory.CAPTION, sample, look(build))
    private fun script(id: String, sample: String, build: TextOverlay.() -> TextOverlay) = TextTemplate(id, TextTemplateCategory.SCRIPT, sample, look(build))
    private fun playful(id: String, sample: String, build: TextOverlay.() -> TextOverlay) = TextTemplate(id, TextTemplateCategory.FUN, sample, look(build))

    val all: List<TextTemplate> = listOf(
        title("headline", "HEADLINE") {
            copy(fontId = BundledFont.ANTON.id, textSizeSp = 44f, stroke = TextStroke(BLACK, 3f), animationIn = TextAnimation(TextAnimationType.POP, 600L))
        },
        title("chapter", "Chapter one") {
            copy(fontId = BundledFont.PLAYFAIR.id, textSizeSp = 34f, italic = true, letterSpacing = 4f, animationIn = TextAnimation(TextAnimationType.FADE, 1_200L))
        },
        title("city", "LAGOS") {
            copy(fontId = BundledFont.BEBAS_NEUE.id, textSizeSp = 64f, letterSpacing = 12f, colorArgb = WHITE, shadow = TextShadow(BLACK, 10f, 0f, 0f), animationIn = TextAnimation(TextAnimationType.SLIDE, 800L))
        },
        title("cinematic", "THE JOURNEY") {
            copy(fontId = BundledFont.CINZEL.id, textSizeSp = 36f, letterSpacing = 8f, shadow = TextShadow(BLACK, 12f, 0f, 0f), animationIn = TextAnimation(TextAnimationType.FADE, 1_500L))
        },
        title("golden", "Golden hour") {
            copy(fontId = BundledFont.ABRIL_FATFACE.id, textSizeSp = 46f, colorArgb = GOLD, shadow = TextShadow(0xCC3D1F00.toInt(), 8f, 4f, 135f), animationIn = TextAnimation(TextAnimationType.FADE, 1_000L))
        },
        title("big_news", "BIG NEWS") {
            copy(fontId = BundledFont.MONTSERRAT.id, textSizeSp = 46f, stroke = TextStroke(BLACK, 2f), animationIn = TextAnimation(TextAnimationType.SLIDE, 500L))
        },
        title("elegant", "Ada & Tunde") {
            copy(fontId = BundledFont.GREAT_VIBES.id, textSizeSp = 54f, glow = TextGlow(GOLD, 0.5f, 10f), animationIn = TextAnimation(TextAnimationType.FADE, 1_500L))
        },
        title("dateline", "LAGOS · 2026") {
            copy(fontId = BundledFont.OSWALD.id, textSizeSp = 30f, letterSpacing = 6f, shadow = TextShadow(BLACK, 6f, 2f, 90f), animationIn = TextAnimation(TextAnimationType.TYPEWRITER, 1_000L))
        },
        title("magazine", "Issue No. 7") {
            copy(fontId = BundledFont.DM_SERIF.id, textSizeSp = 42f, letterSpacing = 2f, shadow = TextShadow(BLACK, 10f, 0f, 0f), animationIn = TextAnimation(TextAnimationType.FADE, 1_000L))
        },
        social("subscribe", "SUBSCRIBE") {
            copy(textSizeSp = 26f, bold = true, colorArgb = WHITE, backgroundArgb = ACCENT, backgroundCorners = 20f, backgroundPadding = 12f, animationLoop = TextAnimation(TextAnimationType.BOUNCE, 1_500L))
        },
        social("follow", "@yourname") {
            copy(fontId = BundledFont.POPPINS.id, textSizeSp = 26f, backgroundArgb = PINK, backgroundCorners = 18f, backgroundPadding = 10f, animationIn = TextAnimation(TextAnimationType.POP, 500L))
        },
        social("sale", "50% OFF") {
            copy(fontId = BundledFont.BANGERS.id, textSizeSp = 58f, colorArgb = YELLOW, stroke = TextStroke(BLACK, 4f), animationIn = TextAnimation(TextAnimationType.POP, 400L), animationLoop = TextAnimation(TextAnimationType.BOUNCE, 1_200L))
        },
        social("new_video", "NEW VIDEO") {
            copy(fontId = BundledFont.RIGHTEOUS.id, textSizeSp = 32f, backgroundArgb = PURPLE, backgroundCorners = 14f, backgroundPadding = 10f, animationIn = TextAnimation(TextAnimationType.SLIDE, 500L))
        },
        social("link_bio", "Link in bio") {
            copy(fontId = BundledFont.POPPINS.id, textSizeSp = 24f, colorArgb = BLACK, backgroundArgb = WHITE, backgroundCorners = 24f, backgroundPadding = 12f, animationLoop = TextAnimation(TextAnimationType.WAVE, 2_000L))
        },
        social("like", "Like & share") {
            copy(fontId = BundledFont.FREDOKA.id, textSizeSp = 34f, glow = TextGlow(PINK, 0.8f, 14f), animationLoop = TextAnimation(TextAnimationType.WAVE, 1_600L))
        },
        caption("lower_third", "Lower third") {
            copy(textSizeSp = 22f, align = TextAlign.LEFT, colorArgb = WHITE, backgroundArgb = BLACK, backgroundOpacity = 0.7f, backgroundCorners = 0f, backgroundPadding = 14f, animationIn = TextAnimation(TextAnimationType.SLIDE, 600L))
        },
        caption("label", "Label") {
            copy(textSizeSp = 24f, colorArgb = BLACK, backgroundArgb = WHITE, backgroundCorners = 12f, backgroundPadding = 10f, bold = true)
        },
        caption("subtitle", "Subtitles like this") {
            copy(textSizeSp = 22f, backgroundArgb = BLACK, backgroundOpacity = 0.6f, backgroundCorners = 6f, backgroundPadding = 8f, animationIn = TextAnimation(TextAnimationType.FADE, 300L))
        },
        caption("bold_caption", "WAIT FOR IT…") {
            copy(fontId = BundledFont.MONTSERRAT.id, textSizeSp = 30f, stroke = TextStroke(BLACK, 3f), animationIn = TextAnimation(TextAnimationType.POP, 300L))
        },
        caption("breaking", "BREAKING NEWS") {
            copy(fontId = BundledFont.OSWALD.id, textSizeSp = 28f, align = TextAlign.LEFT, backgroundArgb = ACCENT, backgroundCorners = 0f, backgroundPadding = 10f, animationIn = TextAnimation(TextAnimationType.SLIDE, 500L))
        },
        caption("quote", "“Do it for you.”") {
            copy(fontId = BundledFont.LORA.id, textSizeSp = 30f, italic = true, shadow = TextShadow(BLACK, 8f, 3f, 135f), animationIn = TextAnimation(TextAnimationType.FADE, 1_200L))
        },
        caption("terminal", "> loading…") {
            copy(fontId = BundledFont.SPACE_MONO.id, textSizeSp = 26f, colorArgb = TERMINAL_GREEN, backgroundArgb = BLACK, backgroundOpacity = 0.8f, backgroundCorners = 4f, backgroundPadding = 10f, animationIn = TextAnimation(TextAnimationType.TYPEWRITER, 1_500L))
        },
        caption("minimal", "a quiet morning") {
            copy(fontId = BundledFont.RALEWAY.id, textSizeSp = 24f, letterSpacing = 3f, shadow = TextShadow(BLACK, 6f, 0f, 0f), animationIn = TextAnimation(TextAnimationType.FADE, 600L))
        },
        script("vibes", "Vibes") {
            copy(fontId = BundledFont.PACIFICO.id, textSizeSp = 40f, colorArgb = 0xFFFFE066.toInt(), glow = TextGlow(PINK, 0.8f, 14f), animationLoop = TextAnimation(TextAnimationType.WAVE, 2_000L))
        },
        script("day", "day 3 ✓") {
            copy(fontId = BundledFont.CAVEAT.id, textSizeSp = 40f, colorArgb = WHITE, shadow = TextShadow(BLACK, 6f, 4f, 135f), animationIn = TextAnimation(TextAnimationType.TYPEWRITER, 1_000L))
        },
        script("marker", "Day 1") {
            copy(fontId = BundledFont.PERMANENT_MARKER.id, textSizeSp = 44f, shadow = TextShadow(BLACK, 4f, 3f, 135f), animationIn = TextAnimation(TextAnimationType.POP, 500L))
        },
        script("signature", "with love") {
            copy(fontId = BundledFont.DANCING_SCRIPT.id, textSizeSp = 44f, shadow = TextShadow(BLACK, 8f, 2f, 135f), animationIn = TextAnimation(TextAnimationType.FADE, 1_200L))
        },
        script("sunday", "Sunday mood") {
            copy(fontId = BundledFont.SATISFY.id, textSizeSp = 42f, colorArgb = PEACH, glow = TextGlow(PEACH, 0.5f, 10f), animationLoop = TextAnimation(TextAnimationType.WAVE, 2_400L))
        },
        script("note", "note to self") {
            copy(fontId = BundledFont.AMATIC_SC.id, textSizeSp = 46f, colorArgb = BLACK, backgroundArgb = PAPER, backgroundCorners = 4f, backgroundPadding = 12f, animationIn = TextAnimation(TextAnimationType.SLIDE, 600L))
        },
        playful("retro", "RETRO") {
            copy(fontId = BundledFont.LOBSTER.id, textSizeSp = 48f, colorArgb = 0xFFFF9F1C.toInt(), stroke = TextStroke(0xFF2EC4B6.toInt(), 4f), curve = 40f)
        },
        playful("comic", "BOOM!") {
            copy(fontId = BundledFont.BANGERS.id, textSizeSp = 64f, colorArgb = YELLOW, stroke = TextStroke(ACCENT, 5f), curve = 20f, animationIn = TextAnimation(TextAnimationType.POP, 400L))
        },
        playful("neon", "OPEN") {
            copy(fontId = BundledFont.RIGHTEOUS.id, textSizeSp = 48f, glow = TextGlow(CYAN, 1f, 18f), animationIn = TextAnimation(TextAnimationType.FADE, 800L))
        },
        playful("bubble", "yay!") {
            copy(fontId = BundledFont.FREDOKA.id, textSizeSp = 54f, colorArgb = BUBBLE_PINK, stroke = TextStroke(WHITE, 5f), animationLoop = TextAnimation(TextAnimationType.BOUNCE, 1_200L))
        },
        playful("level_up", "LEVEL UP") {
            copy(fontId = BundledFont.RIGHTEOUS.id, textSizeSp = 42f, colorArgb = LIME, stroke = TextStroke(BLACK, 4f), curve = -20f, animationIn = TextAnimation(TextAnimationType.POP, 400L))
        },
        playful("party", "PARTY TIME") {
            copy(fontId = BundledFont.MONTSERRAT.id, textSizeSp = 40f, glow = TextGlow(MAGENTA, 0.9f, 16f), animationLoop = TextAnimation(TextAnimationType.WAVE, 1_400L))
        },
    )

    fun inCategory(category: TextTemplateCategory): List<TextTemplate> = all.filter { it.category == category }

    private const val GOLD = 0xFFFFD166.toInt()
    private const val ACCENT = 0xFFD92B50.toInt()
    private const val YELLOW = 0xFFFFE066.toInt()
    private const val PURPLE = 0xFF7B2FF7.toInt()
    private const val PEACH = 0xFFFFB4A2.toInt()
    private const val PAPER = 0xFFFFF3A3.toInt()
    private const val CYAN = 0xFF2EE6FF.toInt()
    private const val BUBBLE_PINK = 0xFFFF6FB5.toInt()
    private const val LIME = 0xFF7CFC00.toInt()
    private const val MAGENTA = 0xFFFF2BD6.toInt()
    private const val TERMINAL_GREEN = 0xFF39FF14.toInt()
}
