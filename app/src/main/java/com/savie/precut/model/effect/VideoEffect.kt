package com.savie.precut.model.effect

import kotlinx.serialization.Serializable

/** The Effects panel's tabs (S-03), in order. */
@Serializable
enum class EffectCategory { BASIC, PARTY, LIGHT, RETRO, GLITCH, ART, WEATHER, LENS, SPLIT }

/**
 * PreCut's bundled video effects (Phase 11, Phase 19), each its own GLSL program
 * (`media/effect/EffectShaders`). Saved by name, so names never change once shipped.
 */
@Serializable
enum class EffectType(val category: EffectCategory) {
    BLUR(EffectCategory.BASIC),
    FLASH(EffectCategory.BASIC),
    BW_FLASH(EffectCategory.BASIC),
    VIGNETTE_PULSE(EffectCategory.BASIC),
    WOBBLE(EffectCategory.BASIC),
    EDGE(EffectCategory.BASIC),
    OLD_FILM(EffectCategory.RETRO),
    FILM_GRAIN(EffectCategory.RETRO),
    POSTERIZE(EffectCategory.RETRO),
    GLITCH(EffectCategory.GLITCH),
    RGB_SPLIT(EffectCategory.GLITCH),
    VHS(EffectCategory.GLITCH),
    SHAKE(EffectCategory.GLITCH),
    STROBE(EffectCategory.GLITCH),
    PIXELATE(EffectCategory.GLITCH),
    LIGHT_LEAK(EffectCategory.LIGHT),
    SOFT_GLOW(EffectCategory.LIGHT),
    BOKEH(EffectCategory.LIGHT),
    SPARKLE(EffectCategory.LIGHT),
    NEON(EffectCategory.LIGHT),
    ZOOM_LENS(EffectCategory.LENS),
    FISHEYE(EffectCategory.LENS),
    MIRROR(EffectCategory.LENS),
    KALEIDOSCOPE(EffectCategory.LENS),
    SPLIT_2(EffectCategory.SPLIT),
    SPLIT_3(EffectCategory.SPLIT),
    SPLIT_4(EffectCategory.SPLIT),
    SPLIT_9(EffectCategory.SPLIT),

    // Phase 19
    HEARTBEAT(EffectCategory.PARTY),
    CONFETTI(EffectCategory.PARTY),
    DISCO(EffectCategory.PARTY),
    RAINBOW(EffectCategory.PARTY),
    INVERT_FLASH(EffectCategory.PARTY),
    LENS_FLARE(EffectCategory.LIGHT),
    DREAMY_GLOW(EffectCategory.LIGHT),
    SPOTLIGHT(EffectCategory.LIGHT),
    CRT(EffectCategory.RETRO),
    TV_STATIC(EffectCategory.GLITCH),
    CHROMATIC_PULSE(EffectCategory.GLITCH),
    DUOTONE(EffectCategory.ART),
    HALFTONE(EffectCategory.ART),
    SKETCH(EffectCategory.ART),
    THERMAL(EffectCategory.ART),
    NIGHT_VISION(EffectCategory.ART),
    SNOW(EffectCategory.WEATHER),
    RAIN(EffectCategory.WEATHER),
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
