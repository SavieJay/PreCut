package com.savie.precut.model.look

import kotlin.math.pow

/** A colour triple. */
data class Rgb(val r: Float, val g: Float, val b: Float) {
    companion object {
        val ZERO = Rgb(0f, 0f, 0f)
        val ONE = Rgb(1f, 1f, 1f)
    }
}

/**
 * A filter recipe, applied to each colour in this order: channel [matrix] → [lift]/[gamma]/[gain]
 * → [mono] → HSL [saturation] → Media3-style [contrast] → [brightness] → split toning → [fade].
 * Values match Media3's own effects (RgbMatrix, HslAdjustment, Contrast, Brightness), so the
 * Phase 4 filters bake to the same look.
 */
data class Grade(
    /** 3x3 row-major: r' = m0 r + m1 g + m2 b, ... */
    val matrix: List<Float>? = null,
    val lift: Rgb = Rgb.ZERO,
    val gamma: Rgb = Rgb.ONE,
    val gain: Rgb = Rgb.ONE,
    /** 0..1 mix towards luma. */
    val mono: Float = 0f,
    /** Added to HSL saturation, -1..1. */
    val saturation: Float = 0f,
    /** -1..1, Media3's Contrast. */
    val contrast: Float = 0f,
    /** Added to every channel. */
    val brightness: Float = 0f,
    val shadowTint: Rgb? = null,
    val highlightTint: Rgb? = null,
    /** 0..1 strength of [shadowTint]/[highlightTint]. */
    val split: Float = 0f,
    /** 0..1: lifts blacks and lowers whites. */
    val fade: Float = 0f,
) {
    /** Grades one colour (0..1 components) and returns the result, clamped. */
    fun apply(r: Float, g: Float, b: Float): Rgb {
        var cr = r
        var cg = g
        var cb = b
        matrix?.let { m ->
            val nr = m[0] * cr + m[1] * cg + m[2] * cb
            val ng = m[3] * cr + m[4] * cg + m[5] * cb
            val nb = m[6] * cr + m[7] * cg + m[8] * cb
            cr = nr; cg = ng; cb = nb
        }
        cr = channel(cr, lift.r, gamma.r, gain.r)
        cg = channel(cg, lift.g, gamma.g, gain.g)
        cb = channel(cb, lift.b, gamma.b, gain.b)
        if (mono > 0f) {
            val y = ColorMath.luma(cr, cg, cb)
            cr += (y - cr) * mono; cg += (y - cg) * mono; cb += (y - cb) * mono
        }
        if (saturation != 0f) {
            val hsl = ColorMath.rgbToHsl(cr.clamp(), cg.clamp(), cb.clamp())
            val rgb = ColorMath.hslToRgb(hsl[0], (hsl[1] + saturation).coerceIn(0f, 1f), hsl[2])
            cr = rgb[0]; cg = rgb[1]; cb = rgb[2]
        }
        if (contrast != 0f) {
            val factor = (1f + contrast) / (1.0001f - contrast)
            cr = (cr - 0.5f) * factor + 0.5f; cg = (cg - 0.5f) * factor + 0.5f; cb = (cb - 0.5f) * factor + 0.5f
        }
        cr += brightness; cg += brightness; cb += brightness
        if (split > 0f) {
            val y = ColorMath.luma(cr.clamp(), cg.clamp(), cb.clamp())
            shadowTint?.let { t ->
                val w = split * (1f - y) * (1f - y)
                cr += (t.r - 0.5f) * w; cg += (t.g - 0.5f) * w; cb += (t.b - 0.5f) * w
            }
            highlightTint?.let { t ->
                val w = split * y * y
                cr += (t.r - 0.5f) * w; cg += (t.g - 0.5f) * w; cb += (t.b - 0.5f) * w
            }
        }
        if (fade > 0f) {
            cr = cr * (1f - 0.3f * fade) + 0.15f * fade
            cg = cg * (1f - 0.3f * fade) + 0.15f * fade
            cb = cb * (1f - 0.3f * fade) + 0.15f * fade
        }
        return Rgb(cr.clamp(), cg.clamp(), cb.clamp())
    }

    private fun channel(x: Float, lift: Float, gamma: Float, gain: Float): Float {
        val lifted = x * gain + lift * (1f - x)
        return if (gamma == 1f) lifted else lifted.clamp().pow(1f / gamma)
    }

    private fun Float.clamp() = coerceIn(0f, 1f)
}

/**
 * A 3D colour lookup table: [size]³ entries of RGB (0..1), red changing fastest, then green, then
 * blue (the `.cube` order).
 */
class Cube(val size: Int, val rgb: FloatArray) {
    init {
        require(size >= 2 && rgb.size == size * size * size * 3) { "Cube data doesn't match size $size" }
    }

    /** Trilinear lookup, as the shader does. */
    fun lookup(r: Float, g: Float, b: Float): Rgb {
        val n = size - 1
        fun axis(v: Float): Triple<Int, Int, Float> {
            val p = v.coerceIn(0f, 1f) * n
            val i = p.toInt().coerceAtMost(n - 1)
            return Triple(i, i + 1, p - i)
        }
        val (r0, r1, tr) = axis(r)
        val (g0, g1, tg) = axis(g)
        val (b0, b1, tb) = axis(b)
        val out = FloatArray(3)
        for (c in 0..2) {
            fun at(ri: Int, gi: Int, bi: Int) = rgb[((bi * size + gi) * size + ri) * 3 + c]
            val c00 = at(r0, g0, b0) + (at(r1, g0, b0) - at(r0, g0, b0)) * tr
            val c10 = at(r0, g1, b0) + (at(r1, g1, b0) - at(r0, g1, b0)) * tr
            val c01 = at(r0, g0, b1) + (at(r1, g0, b1) - at(r0, g0, b1)) * tr
            val c11 = at(r0, g1, b1) + (at(r1, g1, b1) - at(r0, g1, b1)) * tr
            val c0 = c00 + (c10 - c00) * tg
            val c1 = c01 + (c11 - c01) * tg
            out[c] = c0 + (c1 - c0) * tb
        }
        return Rgb(out[0], out[1], out[2])
    }

    /**
     * Packed for a 2D GL texture [size]² wide and [size] tall: blue slices side by side, red
     * across each slice, green down. ARGB ints, opaque.
     */
    fun toTexturePixels(): IntArray {
        val width = size * size
        val pixels = IntArray(width * size)
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            val i = ((b * size + g) * size + r) * 3
            fun byte(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            pixels[g * width + b * size + r] =
                (0xFF shl 24) or (byte(rgb[i]) shl 16) or (byte(rgb[i + 1]) shl 8) or byte(rgb[i + 2])
        }
        return pixels
    }

    companion object {
        const val DEFAULT_SIZE = 33

        /** Bakes [grade] into a cube. */
        fun bake(grade: Grade, size: Int = DEFAULT_SIZE): Cube {
            val data = FloatArray(size * size * size * 3)
            val n = size - 1f
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                val out = grade.apply(r / n, g / n, b / n)
                val i = ((b * size + g) * size + r) * 3
                data[i] = out.r; data[i + 1] = out.g; data[i + 2] = out.b
            }
            return Cube(size, data)
        }
    }
}
