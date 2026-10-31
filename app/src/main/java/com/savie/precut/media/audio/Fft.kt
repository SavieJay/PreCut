package com.savie.precut.media.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** An in-place radix-2 FFT of [size] (a power of two) complex values; the inverse divides by [size]. */
internal class Fft(val size: Int) {
    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2.0 * PI * it / size).toFloat() }
    private val reversed = IntArray(size)

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) reversed[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun forward(re: FloatArray, im: FloatArray) = transform(re, im, inverse = false)

    fun inverse(re: FloatArray, im: FloatArray) {
        transform(re, im, inverse = true)
        val scale = 1f / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        val sign = if (inverse) 1f else -1f
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            for (start in 0 until size step length) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sign * sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
            }
            length *= 2
        }
    }

    companion object {
        /** About 20 ms of sound at [sampleRate], as a power of two (256..2048). */
        fun sizeFor(sampleRate: Int): Int = (Integer.highestOneBit((sampleRate * 0.02f).toInt().coerceAtLeast(1)) * 2).coerceIn(256, 2048)

        /** A periodic square-root Hann window: analysis and synthesis together make a Hann window. */
        fun sqrtHann(size: Int): FloatArray = FloatArray(size) { sqrt(0.5 - 0.5 * cos(2.0 * PI * it / size)).toFloat() }
    }
}
