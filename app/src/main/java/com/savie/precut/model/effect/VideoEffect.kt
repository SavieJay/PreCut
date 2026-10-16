package com.savie.precut.model.effect

import kotlinx.serialization.Serializable

/** The Effects panel's tabs (S-03). */
@Serializable
enum class EffectCategory { BASIC, RETRO, GLITCH, LIGHT, LENS, SPLIT }

/**
 * PreCut's bundled video effects (Phase 11), each a branch of one GLSL program. [shaderId] is
 * the branch the shader runs; it never changes once shipped, so saved projects keep their look.
 */
@Serializable
enum class EffectType(val category: EffectCategory, val shaderId: Int) {
    BLUR(EffectCategory.BASIC, 1),
    FLASH(EffectCategory.BASIC, 2),
    BW_FLASH(EffectCategory.BASIC, 3),
    VIGNETTE_PULSE(EffectCategory.BASIC, 4),
    WOBBLE(EffectCategory.BASIC, 5),
    EDGE(EffectCategory.BASIC, 6),
    OLD_FILM(EffectCategory.RETRO, 7),
    FILM_GRAIN(EffectCategory.RETRO, 8),
    POSTERIZE(EffectCategory.RETRO, 9),
    GLITCH(EffectCategory.GLITCH, 10),
    RGB_SPLIT(EffectCategory.GLITCH, 11),
    VHS(EffectCategory.GLITCH, 12),
    SHAKE(EffectCategory.GLITCH, 13),
    STROBE(EffectCategory.GLITCH, 14),
    PIXELATE(EffectCategory.GLITCH, 15),
    LIGHT_LEAK(EffectCategory.LIGHT, 16),
    SOFT_GLOW(EffectCategory.LIGHT, 17),
    BOKEH(EffectCategory.LIGHT, 18),
    SPARKLE(EffectCategory.LIGHT, 19),
    NEON(EffectCategory.LIGHT, 20),
    ZOOM_LENS(EffectCategory.LENS, 21),
    FISHEYE(EffectCategory.LENS, 22),
    MIRROR(EffectCategory.LENS, 23),
    KALEIDOSCOPE(EffectCategory.LENS, 24),
    SPLIT_2(EffectCategory.SPLIT, 25),
    SPLIT_3(EffectCategory.SPLIT, 26),
    SPLIT_4(EffectCategory.SPLIT, 27),
    SPLIT_9(EffectCategory.SPLIT, 28),
}

/** An effect with its strength (0..1) and speed (how fast it animates, 0.25..4×). */
@Serializable
data class EffectSettings(
    val type: EffectType,
    val intensity: Float = DEFAULT_EFFECT_INTENSITY,
    val speed: Float = 1f,
) {
    fun normalized(): EffectSettings = copy(intensity = intensity.coerceIn(0f, 1f), speed = speed.coerceIn(MIN_EFFECT_SPEED, MAX_EFFECT_SPEED))
}

/** A time-ranged effect over everything under it (an effect layer, S-03/S-04). */
@Serializable
data class EffectLayer(
    val id: String,
    /** Timeline time. */
    val startMs: Long,
    val endMs: Long,
    val effect: EffectSettings,
)

const val DEFAULT_EFFECT_INTENSITY = 0.65f
const val MIN_EFFECT_SPEED = 0.25f
const val MAX_EFFECT_SPEED = 4f
const val DEFAULT_EFFECT_LAYER_MS = 3_000L
