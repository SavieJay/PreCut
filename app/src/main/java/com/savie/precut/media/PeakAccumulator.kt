package com.savie.precut.media

import kotlin.math.abs

/**
 * Collects interleaved 16-bit PCM into peak amplitudes (0..1), one per [framesPerBucket] frames,
 * across all channels. Pure, so waveform bucketing is unit-testable.
 */
class PeakAccumulator(private val framesPerBucket: Int, private val channels: Int) {
    private var peaks = FloatArray(1024)
    private var count = 0
    private var framesInBucket = 0
    private var currentPeak = 0

    init {
        require(framesPerBucket > 0 && channels > 0)
    }

    /** Adds [length] interleaved samples from [samples]. */
    fun add(samples: ShortArray, length: Int = samples.size) {
        var i = 0
        while (i + channels <= length) {
            for (c in 0 until channels) {
                val value = abs(samples[i + c].toInt())
                if (value > currentPeak) currentPeak = value
            }
            i += channels
            if (++framesInBucket == framesPerBucket) closeBucket()
        }
    }

    /** All complete buckets plus a trailing partial one. */
    fun result(): FloatArray {
        if (framesInBucket > 0) closeBucket()
        return peaks.copyOf(count)
    }

    private fun closeBucket() {
        if (count == peaks.size) peaks = peaks.copyOf(peaks.size * 2)
        peaks[count++] = (currentPeak / 32768f).coerceAtMost(1f)
        currentPeak = 0
        framesInBucket = 0
    }
}
