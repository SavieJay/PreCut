package com.savie.precut.media.audio

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Reduce noise (Phase 22B): takes a measured noise [profile] (see [NoiseProfileBuilder]) out of
 * interleaved sound with [channels] channels at [sampleRate], by [strength] (0 leaves it as it is).
 *
 * Short-time spectra (about 20 ms, 75% overlap, square-root Hann windows, so with nothing taken
 * out the sound comes back exactly); each frequency keeps the share of its power that isn't noise,
 * never below a floor, smoothed across neighbouring frequencies and over time so the leftover noise
 * doesn't warble. Every channel gets the same gains, keeping the stereo picture.
 *
 * The overlap delays the sound by a frame less a hop; that much output is dropped at the start and
 * flushed with silence at the end ([finish]), so the output lines up sample for sample with the
 * input and has exactly as many frames.
 */
internal class SpectralDenoiser(sampleRate: Int, private val channels: Int, profile: List<Float>, private val strength: Float) {
    private val size = Fft.sizeFor(sampleRate)
    private val hop = size / 4
    private val bins = size / 2 + 1
    private val fft = Fft(size)
    private val window = Fft.sqrtHann(size)
    private val windowEnergy = window.sumOf { (it * it).toDouble() }.toFloat()
    /** Window overlap: the Hann windows four hops apart sum to 2. */
    private val overlapGain = 2f
    private val noise = NoiseBands.ofBins(size, sampleRate).let { bands -> FloatArray(bins) { profile.getOrElse(bands[it]) { 0f } } }
    private val overSubtraction = 1f + 2f * strength
    private val floor = 10f.pow(-(6f + 18f * strength) / 20f)

    private val input = Array(channels) { FloatArray(size) }
    private val overlap = Array(channels) { FloatArray(size) }
    private val re = Array(channels) { FloatArray(size) }
    private val im = Array(channels) { FloatArray(size) }
    private val raw = FloatArray(bins)
    private val gains = FloatArray(bins)
    private val previous = FloatArray(bins) { 1f }
    private var pending = 0
    private var toDrop = size - hop
    private var framesIn = 0L
    private var framesOut = 0L

    /** Finished interleaved samples: [outputFrames] frames in [output], read with [drain]. */
    private var output = FloatArray(hop * channels * 4)
    private var outputFrames = 0

    /** Adds [frames] interleaved frames from [samples]. */
    fun process(samples: FloatArray, frames: Int) {
        framesIn += frames
        push(samples, frames)
    }

    /** After the last input: the rest of the sound comes out, and the output is exactly as long as the input. */
    fun finish() {
        val silence = FloatArray(hop * channels)
        while (framesOut < framesIn) push(silence, hop)
    }

    /** Hands the finished samples to [consume] (interleaved, [frames] frames) and forgets them. */
    fun drain(consume: (samples: FloatArray, frames: Int) -> Unit) {
        if (outputFrames == 0) return
        consume(output, outputFrames)
        outputFrames = 0
    }

    /** Back to the start (a seek). */
    fun reset() {
        input.forEach { it.fill(0f) }
        overlap.forEach { it.fill(0f) }
        previous.fill(1f)
        pending = 0
        toDrop = size - hop
        framesIn = 0L
        framesOut = 0L
        outputFrames = 0
    }

    private fun push(samples: FloatArray, frames: Int) {
        var frame = 0
        while (frame < frames) {
            val take = minOf(hop - pending, frames - frame)
            for (c in 0 until channels) {
                val target = input[c]
                val start = size - hop + pending
                for (i in 0 until take) target[start + i] = samples[(frame + i) * channels + c]
            }
            pending += take
            frame += take
            if (pending == hop) {
                processFrame()
                pending = 0
            }
        }
    }

    private fun processFrame() {
        for (c in 0 until channels) {
            val x = input[c]
            for (i in 0 until size) {
                re[c][i] = x[i] * window[i]
                im[c][i] = 0f
            }
            fft.forward(re[c], im[c])
        }
        computeGains()
        for (c in 0 until channels) {
            val r = re[c]
            val m = im[c]
            for (k in 0 until bins) {
                r[k] *= gains[k]
                m[k] *= gains[k]
                // The mirrored half keeps the spectrum of a real signal.
                if (k in 1 until size / 2) {
                    r[size - k] = r[k]
                    m[size - k] = -m[k]
                }
            }
            fft.inverse(r, m)
            val sum = overlap[c]
            for (i in 0 until size) sum[i] += r[i] * window[i]
        }
        emit()
        for (c in 0 until channels) {
            input[c].copyInto(input[c], 0, hop, size)
            overlap[c].copyInto(overlap[c], 0, hop, size)
            overlap[c].fill(0f, size - hop, size)
        }
    }

    private fun computeGains() {
        if (strength <= 0f) {
            gains.fill(1f)
            return
        }
        for (k in 0 until bins) {
            var power = 0f
            for (c in 0 until channels) power += re[c][k] * re[c][k] + im[c][k] * im[c][k]
            raw[k] = gainFor(power / channels / windowEnergy, noise[k], overSubtraction, floor)
        }
        for (k in 0 until bins) {
            // A little across frequency, then rising at once but falling gently over time.
            val across = (raw[maxOf(k - 1, 0)] + 2f * raw[k] + raw[minOf(k + 1, bins - 1)]) / 4f
            val g = if (across >= previous[k]) across else previous[k] * RELEASE + across * (1f - RELEASE)
            gains[k] = g
            previous[k] = g
        }
    }

    /** The first hop of the overlap sum is finished: out it goes (less the start-up delay, never past the input's length). */
    private fun emit() {
        var first = 0
        if (toDrop > 0) {
            first = minOf(toDrop, hop)
            toDrop -= first
        }
        val count = minOf(hop - first, (framesIn - framesOut).toInt())
        if (count <= 0) return
        val needed = (outputFrames + count) * channels
        if (output.size < needed) output = output.copyOf(max(needed, output.size * 2))
        var at = outputFrames * channels
        for (i in first until first + count) {
            for (c in 0 until channels) output[at++] = overlap[c][i] / overlapGain
        }
        outputFrames += count
        framesOut += count
    }

    companion object {
        /** How much of last frame's gain stays when it falls (per hop). */
        private const val RELEASE = 0.5f

        /**
         * The amplitude kept at a frequency with [power] where the noise is [noise]: the share of
         * power that isn't noise ([overSubtraction] times it, so a little more goes), never below [floor].
         */
        fun gainFor(power: Float, noise: Float, overSubtraction: Float, floor: Float): Float {
            if (noise <= 0f) return 1f
            val kept = 1f - overSubtraction * noise / max(power, 1e-20f)
            return sqrt(max(kept, floor * floor))
        }
    }
}
