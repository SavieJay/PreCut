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
