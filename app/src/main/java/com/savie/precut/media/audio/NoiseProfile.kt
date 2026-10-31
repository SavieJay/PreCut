package com.savie.precut.media.audio

import kotlin.math.ln

/**
 * The frequency bands a noise profile is kept in, the same at every sample rate: below 100 Hz, then
 * 31 bands evenly spaced in pitch up to 24 kHz (anything higher counts in the last).
 */
internal object NoiseBands {
    const val COUNT = 32
    private const val LOW_HZ = 100f
    private const val HIGH_HZ = 24_000f

    fun of(hz: Float): Int {
        if (hz < LOW_HZ) return 0
        val position = ln(hz / LOW_HZ) / ln(HIGH_HZ / LOW_HZ)
        return (1 + (position * (COUNT - 1)).toInt()).coerceAtMost(COUNT - 1)
    }

    /** The band of each FFT bin (0 .. size / 2) at [sampleRate]. */
    fun ofBins(size: Int, sampleRate: Int): IntArray = IntArray(size / 2 + 1) { of(it * sampleRate.toFloat() / size) }
}

/**
 * Measures a clip's background noise (Phase 22B) from its sound, given as mono samples at
 * [sampleRate]: the sound is cut into frames, the quietest fifth of them (by overall loudness:
 * the pauses, where speech and music are absent and steady noise remains) are kept, and each
 * band's level is their average. Digital silence doesn't count. The levels are power per FFT bin
 * over the window's energy, so [SpectralDenoiser] compares like with like at any frame size; a
 * band with no bins at this rate takes its neighbour's level.
 */
internal class NoiseProfileBuilder(private val sampleRate: Int) {
    private val size = Fft.sizeFor(sampleRate)
    private val fft = Fft(size)
    private val window = Fft.sqrtHann(size)
    private val windowEnergy = window.sumOf { (it * it).toDouble() }.toFloat()
    private val bandOfBin = NoiseBands.ofBins(size, sampleRate)
    private val binsInBand = IntArray(NoiseBands.COUNT).also { counts -> bandOfBin.forEach { counts[it]++ } }
    private val frame = FloatArray(size)
    private var filled = 0
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    /** Per measured frame: its average power per bin, then its band levels. */
    private val frames = ArrayList<Pair<Float, FloatArray>>()

    /** Adds [count] mono samples. */
    fun add(samples: FloatArray, count: Int) {
        for (i in 0 until count) {
            frame[filled++] = samples[i]
            if (filled == size) {
                measure()
                filled = 0
            }
        }
    }

    /** Each band's noise level; null when there was too little sound to tell (under [MIN_FRAMES] frames). */
    fun build(): List<Float>? {
        if (frames.size < MIN_FRAMES) return null
        val quiet = frames.sortedBy { it.first }.take(maxOf(MIN_QUIET_FRAMES, (frames.size * QUIET_SHARE).toInt()))
        val levels = FloatArray(NoiseBands.COUNT) { band -> quiet.sumOf { it.second[band].toDouble() }.toFloat() / quiet.size }
        // Bands with no bins at this rate (narrow low bands) take the nearest measured one, below first.
        val measured = (0 until NoiseBands.COUNT).filter { binsInBand[it] > 0 }
        return (0 until NoiseBands.COUNT).map { band ->
            if (binsInBand[band] > 0) levels[band] else levels[measured.lastOrNull { it < band } ?: measured.first { it > band }]
        }
    }

    private fun measure() {
        for (i in 0 until size) {
            re[i] = frame[i] * window[i]
            im[i] = 0f
        }
        fft.forward(re, im)
        val bands = FloatArray(NoiseBands.COUNT)
        var total = 0f
        for (k in bandOfBin.indices) {
            val power = (re[k] * re[k] + im[k] * im[k]) / windowEnergy
            bands[bandOfBin[k]] += power
            total += power
        }
        val average = total / bandOfBin.size
        if (average < SILENCE) return
        for (band in bands.indices) if (binsInBand[band] > 0) bands[band] /= binsInBand[band]
        frames += average to bands
    }

    companion object {
        /** About 0.2 s of sound at 48 kHz. */
        const val MIN_FRAMES = 8
        /** The quietest fifth of the frames, and at least this many. */
        private const val QUIET_SHARE = 0.2f
        private const val MIN_QUIET_FRAMES = 4
        /** Average power per bin below this is digital silence (about −90 dBFS). */
        private const val SILENCE = 1e-9f
    }
}
