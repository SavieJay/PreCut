package com.savie.precut.model.template

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.MAX_IMAGE_DURATION_MS
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.effect.EffectLayer
import com.savie.precut.model.effect.EffectSettings
import com.savie.precut.model.effect.EffectType
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.motion.ComboType
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.text.BundledFont
import com.savie.precut.model.text.TextAnimation
import com.savie.precut.model.text.TextAnimationType
import com.savie.precut.model.text.TextShadow
import com.savie.precut.model.text.TextStroke

/**
 * The starter pack: two templates per category, made only of PreCut's own looks, effects,
 * transitions, animations, text styles and bundled open-licence fonts (no footage or music,
 * so nothing else to license). Written as code, not JSON files, so the compiler checks every
 * name; [BuiltInTemplatesTest] checks each one still saves, loads and fills.
 */
object BuiltInTemplates {
    const val PREFIX = "builtin."

    val all: List<ProjectTemplate> by lazy {
        listOf(
            cityLights(), roadTrip(), quickCuts(), beforeAfter(), productSpotlight(), grandOpening(),
            birthday(), weddingDay(), memories(), photoDump(), vlogIntro(), dayInMyLife(),
        )
    }

    fun byId(id: String): ProjectTemplate? = all.firstOrNull { it.id == id }

    private fun cityLights() = template(
        "city_lights", "City lights", TemplateCategory.TRAVEL,
        clips = List(6) { slot(it, 1_200, look = "film.teal_orange", transition = ClipTransition(TransitionType.ZOOM_IN, 400), combo = ComboType.ZOOM_IN_OUT) },
        texts = listOf(title("CITY LIGHTS", 0, 2_400, BundledFont.BEBAS_NEUE, size = 64f, animation = TextAnimationType.FADE)),
    )

    private fun roadTrip() = template(
        "road_trip", "Road trip", TemplateCategory.TRAVEL,
        clips = List(8) { slot(it, 900, look = "retro.70s", transition = ClipTransition(if (it % 2 == 0) TransitionType.SLIDE else TransitionType.PUSH, 300)) },
        texts = listOf(title("Road trip", 0, 2_000, BundledFont.CAVEAT, size = 56f, animation = TextAnimationType.TYPEWRITER, y = 0.8f)),
        effects = { end -> listOf(EffectLayer("grain", 0, end, EffectSettings(EffectType.FILM_GRAIN, 0.5f))) },
    )

    private fun quickCuts() = template(
        "quick_cuts", "Quick cuts", TemplateCategory.SOCIAL,
        clips = List(10) { slot(it, 500, transition = ClipTransition(TransitionType.GLITCH, 200).takeIf { _ -> it % 3 == 2 }) },
        texts = listOf(title("WAIT FOR IT", 0, 1_500, BundledFont.ANTON, size = 52f, animation = TextAnimationType.POP, stroke = true, y = 0.75f)),
        effects = { end -> listOf(EffectLayer("split", 0, end, EffectSettings(EffectType.RGB_SPLIT, 0.3f))) },
    )

    private fun beforeAfter() = template(
        "before_after", "Before & after", TemplateCategory.SOCIAL,
        clips = listOf(slot(0, 3_000, transition = ClipTransition(TransitionType.WIPE, 800)), slot(1, 3_000)),
        texts = listOf(
            label("BEFORE", 0, 2_600, y = 0.12f),
            label("AFTER", 3_200, 5_200, y = 0.12f),
        ),
    )

    private fun productSpotlight() = template(
        "product_spotlight", "Product spotlight", TemplateCategory.BUSINESS, AspectRatio.RATIO_1_1,
        clips = listOf(2_500L, 2_000L, 2_000L, 2_500L).mapIndexed { i, ms -> slot(i, ms, look = "life.clear", transition = ClipTransition(TransitionType.DISSOLVE, 500), animation = AnimationType.ZOOM.takeIf { i == 0 }) },
        texts = listOf(
            title("New collection", 0, 2_500, BundledFont.PLAYFAIR, size = 44f, animation = TextAnimationType.FADE),
            label("From ₦15,000", 3_000, 5_500, y = 0.85f),
            label("Shop now", 6_000, 7_500, y = 0.85f, background = ACCENT),
        ),
    )

    private fun grandOpening() = template(
        "grand_opening", "Grand opening", TemplateCategory.BUSINESS,
        clips = List(5) { slot(it, 1_800, look = "basic.vivid", transition = ClipTransition(TransitionType.PUSH, 400)) },
        texts = listOf(
            title("GRAND OPENING", 0, 2_200, BundledFont.ANTON, size = 54f, animation = TextAnimationType.BOUNCE, stroke = true),
            label("This Saturday · 10 am", 5_000, 7_400, y = 0.85f),
        ),
        effects = { _ -> listOf(EffectLayer("light", 0, 1_800, EffectSettings(EffectType.LIGHT_LEAK, 0.6f))) },
    )

    private fun birthday() = template(
        "birthday", "Happy birthday", TemplateCategory.CELEBRATION,
        clips = List(6) { slot(it, 2_000, animation = AnimationType.ZOOM, transition = ClipTransition(TransitionType.FLASH, 300)) },
        texts = listOf(
            title("Happy birthday", 0, 4_000, BundledFont.PACIFICO, size = 56f, color = GOLD, animation = TextAnimationType.WAVE),
            label("Name", 4_000, 7_000, y = 0.8f),
        ),
        effects = { end -> listOf(EffectLayer("sparkle", 0, end, EffectSettings(EffectType.SPARKLE, 0.6f))) },
    )

    private fun weddingDay() = template(
        "wedding_day", "Wedding day", TemplateCategory.CELEBRATION,
        clips = List(6) { slot(it, 2_500, look = "life.soft", transition = ClipTransition(TransitionType.DISSOLVE, 800)) },
        texts = listOf(title("Ada & Tunde", 0, 3_500, BundledFont.PLAYFAIR, size = 48f, animation = TextAnimationType.FADE)),
        effects = { end -> listOf(EffectLayer("glow", 0, end, EffectSettings(EffectType.SOFT_GLOW, 0.4f))) },
    )

    private fun memories() = template(
        "memories", "Memories", TemplateCategory.SLIDESHOW,
        clips = List(8) { slot(it, 2_000, look = "retro.faded", transition = ClipTransition(TransitionType.DISSOLVE, 700), combo = ComboType.ZOOM_IN_OUT) },
        texts = listOf(title("Memories", 0, 3_000, BundledFont.CAVEAT, size = 60f, animation = TextAnimationType.FADE)),
    )

    private fun photoDump() = template(
        "photo_dump", "Photo dump", TemplateCategory.SLIDESHOW,
        clips = List(12) { slot(it, 600, transition = ClipTransition(TransitionType.FLASH, 200).takeIf { _ -> it % 4 == 3 }) },
        texts = listOf(label("photo dump", 0, 1_800, y = 0.5f, font = BundledFont.SPACE_MONO)),
    )

    private fun vlogIntro() = template(
        "vlog_intro", "Vlog intro", TemplateCategory.VLOG,
        clips = List(4) { slot(it, 1_500, transition = ClipTransition(TransitionType.ZOOM_OUT, 400)) },
        texts = listOf(
            title("Day 1", 0, 2_000, BundledFont.BEBAS_NEUE, size = 72f, animation = TextAnimationType.POP),
            label("@yourchannel", 3_000, 6_000, y = 0.88f),
        ),
    )

    private fun dayInMyLife() = template(
        "day_in_my_life", "Day in my life", TemplateCategory.VLOG,
        clips = List(7) { slot(it, 1_600, look = "life.fresh", transition = ClipTransition(TransitionType.SLIDE, 300)) },
        texts = listOf(
            label("7:00 am", 0, 1_400, y = 0.15f, font = BundledFont.SPACE_MONO),
            label("12:30 pm", 4_000, 5_400, y = 0.15f, font = BundledFont.SPACE_MONO),
            label("9:00 pm", 8_000, 9_400, y = 0.15f, font = BundledFont.SPACE_MONO),
        ),
    )

    /** A slot: a placeholder photo of [lengthMs], filling the frame (any picked media fills it). */
    private fun slot(
        index: Int,
        lengthMs: Long,
        look: String? = null,
        transition: ClipTransition? = null,
        animation: AnimationType? = null,
        combo: ComboType? = null,
    ) = Clip(
        id = "slot$index",
        uri = SLOT_URI_PREFIX + index,
        type = ClipType.IMAGE,
        sourceDurationMs = MAX_IMAGE_DURATION_MS,
        trimEndMs = lengthMs,
        fit = FrameFit.FILL,
        look = look?.let { ClipLook(filterId = it) } ?: ClipLook(),
        transitionOut = transition,
        animationIn = animation?.let { ClipAnimation(it) },
        animationCombo = combo?.let { ComboAnimation(it) },
    )

    // Text ids only need to be unique within a template; [all] is built once, in order, so they're stable.
    private var textCount = 0

    private fun title(
        text: String,
        startMs: Long,
        endMs: Long,
        font: BundledFont,
        size: Float,
        color: Int = WHITE,
        animation: TextAnimationType,
        stroke: Boolean = false,
        y: Float = 0.5f,
    ) = TextOverlay(
        id = "text${textCount++}", text = text, startMs = startMs, endMs = endMs, yNorm = y, textSizeSp = size, colorArgb = color,
        fontId = font.id, stroke = TextStroke(BLACK, 4f).takeIf { stroke }, shadow = TextShadow(SHADOW),
        animationIn = TextAnimation(animation), animationOut = TextAnimation(TextAnimationType.FADE, 400),
    )

    private fun label(text: String, startMs: Long, endMs: Long, y: Float, background: Int = BLACK_70, font: BundledFont? = null) = TextOverlay(
        id = "text${textCount++}", text = text, startMs = startMs, endMs = endMs, yNorm = y, textSizeSp = 24f, colorArgb = WHITE,
        backgroundArgb = background, backgroundCorners = 0.4f, fontId = font?.id, bold = font == null,
        animationIn = TextAnimation(TextAnimationType.SLIDE, 500), animationOut = TextAnimation(TextAnimationType.FADE, 400),
    )

    private fun template(
        key: String,
        title: String,
        category: TemplateCategory,
        aspect: AspectRatio = AspectRatio.RATIO_9_16,
        clips: List<Clip>,
        texts: List<TextOverlay>,
        effects: (endMs: Long) -> List<EffectLayer> = { emptyList() },
    ): ProjectTemplate {
        val id = PREFIX + key
        val base = Project(id = id, name = title, aspectRatio = aspect, clips = clips, textOverlays = texts, createdAt = 0, updatedAt = 0)
        val project = base.copy(effectLayers = effects(base.totalDurationMs()))
        return ProjectTemplate(
            id = id,
            title = title,
            project = project,
            slots = clips.map { TemplateSlot(it.id) },
            texts = texts.map { TemplateText(it.id) },
            category = category,
            credits = listOf("Fonts: SIL Open Font License 1.1 (see Settings → Open-source licences)"),
        )
    }

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val BLACK_70 = 0xB3000000.toInt()
    private const val SHADOW = 0x99000000.toInt()
    private const val GOLD = 0xFFFFD43B.toInt()
    private const val ACCENT = 0xFFD92B50.toInt()
}
