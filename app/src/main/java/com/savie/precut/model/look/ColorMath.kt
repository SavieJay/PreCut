package com.savie.precut.model.look

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Colour helpers shared by LUT baking, the shader's curve table and CPU thumbnails. */
object ColorMath {

    /** Rec. 709 luma, the weights Media3's grayscale filter uses. */
    fun luma(r: Float, g: Float, b: Float): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

    /** RGB (0..1) to HSL: hue in degrees 0..360, saturation and lightness 0..1. */
    fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val l = (maxC + minC) / 2f
        val d = maxC - minC
        if (d < 1e-6f) return floatArrayOf(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (maxC) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
    }

    /** HSL (hue in degrees) to RGB 0..1. */
    fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r, g, b) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return floatArrayOf(r + m, g + m, b + m)
    }

    /**
     * A tone curve through [points] (x and y in 0..1) sampled into [size] values, using a monotone
     * cubic (Fritsch–Carlson) so it never overshoots between points. Outside the first/last point
     * the curve is flat.
     */
    fun curveTable(points: List<CurvePoint>, size: Int = 256): FloatArray {
        val p = points.sortedBy { it.x }.distinctBy { it.x }
        if (p.isEmpty()) return FloatArray(size) { it / (size - 1f) }
        if (p.size == 1) return FloatArray(size) { p[0].y.coerceIn(0f, 1f) }
        val n = p.size
        val dx = FloatArray(n - 1) { p[it + 1].x - p[it].x }
        val slope = FloatArray(n - 1) { (p[it + 1].y - p[it].y) / dx[it] }
        val tangent = FloatArray(n)
        tangent[0] = slope[0]
        tangent[n - 1] = slope[n - 2]
        for (i in 1 until n - 1) {
            tangent[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else (slope[i - 1] + slope[i]) / 2f
        }
        for (i in 0 until n - 1) {
            if (slope[i] == 0f) {
                tangent[i] = 0f
                tangent[i + 1] = 0f
                continue
            }
            val a = tangent[i] / slope[i]
            val b = tangent[i + 1] / slope[i]
            val h = a * a + b * b
            if (h > 9f) {
                val t = 3f / sqrt(h)
                tangent[i] = t * a * slope[i]
                tangent[i + 1] = t * b * slope[i]
            }
        }
        return FloatArray(size) { index ->
            val x = index / (size - 1f)
            val y = when {
                x <= p.first().x -> p.first().y
                x >= p.last().x -> p.last().y
                else -> {
                    val i = (0 until n - 1).first { x <= p[it + 1].x }
                    val t = (x - p[i].x) / dx[i]
                    val t2 = t * t
                    val t3 = t2 * t
                    (2 * t3 - 3 * t2 + 1) * p[i].y + (t3 - 2 * t2 + t) * dx[i] * tangent[i] +
                        (-2 * t3 + 3 * t2) * p[i + 1].y + (t3 - t2) * dx[i] * tangent[i + 1]
                }
            }
            y.coerceIn(0f, 1f)
        }
    }

    /** Reads a 0..1 table (like [curveTable]) at [x] with linear interpolation. */
    fun sample(table: FloatArray, x: Float): Float {
        val pos = x.coerceIn(0f, 1f) * (table.size - 1)
        val i = pos.toInt().coerceAtMost(table.size - 2)
        return table[i] + (table[i + 1] - table[i]) * (pos - i)
    }
}
