package com.savie.precut.media.cutout

import kotlin.math.roundToInt

/**
 * Turns the segmenter's per-frame person confidence (0..1, [width] × [height]) into matte bytes:
 *
 * 1. Steadied over time: small changes from one frame to the next (the model's noise) are damped,
 *    large ones (real movement) come through at once, so the outline doesn't shimmer or lag.
 * 2. Softened a little (two box blurs), so the edge has a gradient the shader's Edge and Softness
 *    can move along.
 * 3. Near-certain pixels snapped to 0 or 255, which keeps the file small.
 *
 * Feed frames in time order; one smoother per clip.
 */
class MatteSmoother(private val width: Int, private val height: Int) {
    private var previous: FloatArray? = null
    private val steadied = FloatArray(width * height)
    private val scratch = FloatArray(width * height)

    fun next(confidence: FloatArray, out: ByteArray) {
        require(confidence.size == width * height && out.size == width * height) { "Wrong matte size" }
        val before = previous
        for (i in confidence.indices) {
            val now = confidence[i].coerceIn(0f, 1f)
            steadied[i] = if (before == null) {
                now
            } else {
                val change = now - before[i]
                before[i] + change * (STEADY + change * change * FOLLOW).coerceAtMost(1f)
            }
        }
        previous = (before ?: FloatArray(width * height)).also { steadied.copyInto(it) }
        // Blur a copy: the steadied values stay unblurred for the next frame.
        steadied.copyInto(scratch)
        repeat(BLUR_PASSES) {
            boxBlurRows(scratch)
            boxBlurColumns(scratch)
        }
        for (i in out.indices) out[i] = quantize(scratch[i])
    }

    private fun boxBlurRows(values: FloatArray) {
        val row = FloatArray(width)
        for (y in 0 until height) {
            val base = y * width
            for (x in 0 until width) {
                var sum = 0f
                for (k in -BLUR_RADIUS..BLUR_RADIUS) sum += values[base + (x + k).coerceIn(0, width - 1)]
                row[x] = sum / (2 * BLUR_RADIUS + 1)
            }
            row.copyInto(values, base)
        }
    }

    private fun boxBlurColumns(values: FloatArray) {
        val column = FloatArray(height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                var sum = 0f
                for (k in -BLUR_RADIUS..BLUR_RADIUS) sum += values[(y + k).coerceIn(0, height - 1) * width + x]
                column[y] = sum / (2 * BLUR_RADIUS + 1)
            }
            for (y in 0 until height) values[y * width + x] = column[y]
        }
    }

    companion object {
        /** How much of a small change comes through per frame. */
        private const val STEADY = 0.3f
        /** How fast larger changes come through fully (a change of about 0.42 passes at once). */
        private const val FOLLOW = 4f
        private const val BLUR_RADIUS = 1
        private const val BLUR_PASSES = 2
        private const val SNAP = 0.02f

        fun quantize(value: Float): Byte = when {
            value <= SNAP -> 0
            value >= 1f - SNAP -> 255
            else -> (value * 255f).roundToInt()
        }.toByte()

        /** The share of a matte picture that is mostly person (above half). */
        fun personShare(pixels: ByteArray): Float = pixels.count { (it.toInt() and 0xFF) >= 128 }.toFloat() / pixels.size
    }
}
