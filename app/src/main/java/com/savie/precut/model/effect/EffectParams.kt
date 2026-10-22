package com.savie.precut.model.effect

/**
 * What the effect shader gets for an effect (pure, so it's unit-tested): four parameters derived
 * from the type and intensity, and the time it animates by.
 */
object EffectParams {

    /** `uParams` for [settings]: what each shader branch reads, documented per type. */
    fun of(settings: EffectSettings): FloatArray {
        val i = settings.intensity.coerceIn(0f, 1f)
        return when (settings.type) {
            // Blur radius (fraction of the short side).
            EffectType.BLUR -> floatArrayOf(lerp(0.002f, 0.03f, i), 0f, 0f, 0f)
            // Flash strength; flashes per second at 1×.
            EffectType.FLASH -> floatArrayOf(i, 1.5f, 0f, 0f)
            EffectType.BW_FLASH -> floatArrayOf(i, 2f, 0f, 0f)
            // Darkest edge; pulses per second.
            EffectType.VIGNETTE_PULSE -> floatArrayOf(lerp(0.2f, 0.9f, i), 1f, 0f, 0f)
            // Wave amplitude (uv), waves across the frame.
            EffectType.WOBBLE -> floatArrayOf(lerp(0.002f, 0.025f, i), 12f, 0f, 0f)
            // How much of the frame becomes edges.
            EffectType.EDGE -> floatArrayOf(i, 0f, 0f, 0f)
            // Sepia amount, scratches, grain, flicker.
            EffectType.OLD_FILM -> floatArrayOf(lerp(0.4f, 1f, i), i, lerp(0.05f, 0.18f, i), lerp(0.02f, 0.1f, i))
            EffectType.FILM_GRAIN -> floatArrayOf(lerp(0.03f, 0.25f, i), 0f, 0f, 0f)
            // Colour levels per channel.
            EffectType.POSTERIZE -> floatArrayOf(lerp(12f, 3f, i), 0f, 0f, 0f)
            // Share of glitched rows, how far they shift, colour split.
            EffectType.GLITCH -> floatArrayOf(lerp(0.05f, 0.45f, i), lerp(0.02f, 0.12f, i), lerp(0.002f, 0.015f, i), 0f)
            EffectType.RGB_SPLIT -> floatArrayOf(lerp(0.002f, 0.025f, i), 0f, 0f, 0f)
            // Scanline strength, wave, colour bleed, noise.
            EffectType.VHS -> floatArrayOf(lerp(0.05f, 0.35f, i), lerp(0.001f, 0.008f, i), lerp(0.001f, 0.006f, i), lerp(0.02f, 0.15f, i))
            // Shake distance (uv) and the zoom that hides the edges.
            EffectType.SHAKE -> lerp(0.004f, 0.04f, i).let { floatArrayOf(it, 1f + it * 2.2f, 0f, 0f) }
            // Darkness of the off beats; beats per second.
            EffectType.STROBE -> floatArrayOf(lerp(0.3f, 0.95f, i), 6f, 0f, 0f)
            // Cells across the short side.
            EffectType.PIXELATE -> floatArrayOf(lerp(120f, 14f, i), 0f, 0f, 0f)
            EffectType.LIGHT_LEAK -> floatArrayOf(lerp(0.2f, 0.85f, i), 0f, 0f, 0f)
            // Glow strength, radius, threshold.
            EffectType.SOFT_GLOW -> floatArrayOf(lerp(0.2f, 0.9f, i), lerp(0.008f, 0.03f, i), 0.55f, 0f)
            // Background blur radius, highlight discs.
            EffectType.BOKEH -> floatArrayOf(lerp(0.01f, 0.035f, i), lerp(0.2f, 0.8f, i), 0f, 0f)
            // Sparkle density and brightness.
            EffectType.SPARKLE -> floatArrayOf(lerp(0.15f, 0.6f, i), lerp(0.5f, 1f, i), 0f, 0f)
            // Edge glow over a darkened frame.
            EffectType.NEON -> floatArrayOf(lerp(0.3f, 1f, i), lerp(0.4f, 0.85f, i), 0f, 0f)
            // Zoom amount; pulses per second.
            EffectType.ZOOM_LENS -> floatArrayOf(lerp(0.05f, 0.4f, i), 0.8f, 0f, 0f)
            // Barrel strength.
            EffectType.FISHEYE -> floatArrayOf(lerp(0.1f, 0.9f, i), 0f, 0f, 0f)
            // Blend of the mirrored half.
            EffectType.MIRROR -> floatArrayOf(i, 0f, 0f, 0f)
            // Segments; turns per second.
            EffectType.KALEIDOSCOPE -> floatArrayOf((4f + (i * 4f).toInt() * 2f), 0.05f, 0f, 0f)
            // Columns, rows, gap (uv).
            EffectType.SPLIT_2 -> floatArrayOf(1f, 2f, gap(i), 0f)
            EffectType.SPLIT_3 -> floatArrayOf(1f, 3f, gap(i), 0f)
            EffectType.SPLIT_4 -> floatArrayOf(2f, 2f, gap(i), 0f)
            EffectType.SPLIT_9 -> floatArrayOf(3f, 3f, gap(i), 0f)
            // Zoom at the beat; beats per second (72 BPM).
            EffectType.HEARTBEAT -> floatArrayOf(lerp(0.03f, 0.14f, i), 1.2f, 0f, 0f)
            // Share of cells with a piece of confetti or a snowflake / raindrop; how much it cools or darkens the frame.
            EffectType.CONFETTI -> floatArrayOf(lerp(0.15f, 0.55f, i), 0f, 0f, 0f)
            EffectType.SNOW -> floatArrayOf(lerp(0.12f, 0.5f, i), lerp(0.1f, 0.4f, i), 0f, 0f)
            EffectType.RAIN -> floatArrayOf(lerp(0.15f, 0.5f, i), lerp(0.1f, 0.35f, i), 0f, 0f)
            // Strength of the coloured lights.
            EffectType.DISCO -> floatArrayOf(lerp(0.3f, 1f, i), 0f, 0f, 0f)
            // Mix; colour-wheel turns per second.
            EffectType.RAINBOW -> floatArrayOf(lerp(0.25f, 0.9f, i), 0.25f, 0f, 0f)
            // Mix of the inverted picture; flips per second.
            EffectType.INVERT_FLASH -> floatArrayOf(lerp(0.5f, 1f, i), 2f, 0f, 0f)
            EffectType.LENS_FLARE -> floatArrayOf(lerp(0.3f, 1f, i), 0f, 0f, 0f)
            // Glow strength, blur radius.
            EffectType.DREAMY_GLOW -> floatArrayOf(lerp(0.2f, 0.9f, i), lerp(0.01f, 0.035f, i), 0f, 0f)
            // Darkness outside the light; the light's radius (short side).
            EffectType.SPOTLIGHT -> floatArrayOf(lerp(0.3f, 0.85f, i), 0.32f, 0f, 0f)
            // Screen curve, lines across the height, line darkness.
            EffectType.CRT -> floatArrayOf(lerp(0.08f, 0.3f, i), 240f, lerp(0.15f, 0.45f, i), 0f)
            // Noise mix, rolling bar darkness.
            EffectType.TV_STATIC -> floatArrayOf(lerp(0.12f, 0.6f, i), lerp(0.1f, 0.35f, i), 0f, 0f)
            // Channel split at the corners (uv); pulses per second.
            EffectType.CHROMATIC_PULSE -> floatArrayOf(lerp(0.008f, 0.04f, i), 2f, 0f, 0f)
            // Mix of the recoloured picture.
            EffectType.DUOTONE -> floatArrayOf(lerp(0.5f, 1f, i), 0f, 0f, 0f)
            EffectType.THERMAL -> floatArrayOf(lerp(0.6f, 1f, i), 0f, 0f, 0f)
            // Dots across the short side.
            EffectType.HALFTONE -> floatArrayOf(lerp(90f, 30f, i), 0f, 0f, 0f)
            // Mix of the drawing; pencil line strength.
            EffectType.SKETCH -> floatArrayOf(lerp(0.5f, 1f, i), lerp(2f, 4f, i), 0f, 0f)
            // Mix; noise.
            EffectType.NIGHT_VISION -> floatArrayOf(lerp(0.6f, 1f, i), lerp(0.04f, 0.15f, i), 0f, 0f)
        }
    }

    /**
     * Seconds the effect has run at its speed, [presentationUs] into a range that starts at
     * [startUs]; the shader animates by this.
     */
    fun timeSeconds(presentationUs: Long, startUs: Long, speed: Float): Float =
        ((presentationUs - startUs).coerceAtLeast(0L) / 1_000_000.0 * speed.coerceIn(MIN_EFFECT_SPEED, MAX_EFFECT_SPEED)).toFloat()

    /** Whether a layer covering [startUs] until [endUs] shows at [presentationUs]; null bounds mean always. */
    fun isActive(presentationUs: Long, startUs: Long?, endUs: Long?): Boolean =
        (startUs == null || presentationUs >= startUs) && (endUs == null || presentationUs < endUs)

    private fun gap(i: Float) = i * 0.02f

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
