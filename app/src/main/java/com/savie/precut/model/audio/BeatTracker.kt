package com.savie.precut.model.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Beats found in a track: [beatsMs] ascending, in the track's own time; [bpm] 0 when there were none. */
data class BeatResult(val beatsMs: List<Long>, val bpm: Float, val strengths: List<Float>) {

    /**
     * Every [every]th beat, starting from whichever beat makes the strongest set: in most music
     * that's the bar's first beat, where the kick and the chord changes land.
     */
    fun every(every: Int): List<Long> {
        if (every <= 1 || beatsMs.size <= every) return beatsMs
        val offset = (0 until every).maxBy { start -> strengths.indices.filter { it % every == start }.sumOf { strengths[it].toDouble() } }
        return beatsMs.filterIndexed { i, _ -> i % every == offset }
    }

    companion object {
        val NONE = BeatResult(emptyList(), 0f, emptyList())
    }
}

/**
 * Finds the beats in 16-bit audio fed in order with [add]; no AI, no model, the standard method:
 * 1. **onset strength:** the sound reduced to mono near [TARGET_RATE], cut into short FFT frames
 *    ([FRAME] samples every [HOP], about 86 a second), and the rise in log-magnitude summed over
 *    frequencies (spectral flux): high where a note or drum starts;
 * 2. **tempo:** the autocorrelation of that curve between [MIN_BPM] and [MAX_BPM], gently
 *    weighted towards 120 BPM so half or double the tempo isn't picked;
 * 3. **beats:** Ellis's dynamic programming (2007): the sequence that best lands on onsets while
 *    keeping close to the tempo, so it follows small drifts.
 * [startMs] is the time of the first sample fed (decoders may skip an encoder's priming).
 */
class BeatTracker(sampleRate: Int, private val channels: Int, private val startMs: Long = 0L) {
    private val step = max(1, (sampleRate / TARGET_RATE.toDouble()).roundToInt())
    private val rate = sampleRate.toDouble() / step

    // Decimation (average of [step] mono samples).
    private var sum = 0f
    private var summed = 0

    // Framing: the last FRAME samples, refreshed every HOP.
    private val window = FloatArray(FRAME)
    private val incoming = FloatArray(HOP)
    private var filled = 0
    private var pending = 0

    private val fft = Fft(FRAME)
    private val hann = FloatArray(FRAME) { (0.5 - 0.5 * cos(2 * PI * it / FRAME)).toFloat() }
    private val re = FloatArray(FRAME)
    private val im = FloatArray(FRAME)
    private var previous = FloatArray(FRAME / 2)
    private var current = FloatArray(FRAME / 2)
    private var hasPrevious = false

    private var flux = FloatArray(1024)
    private var energy = FloatArray(1024)
    private var frames = 0

    init {
        require(sampleRate > 0 && channels > 0)
    }

    /** Adds [count] interleaved samples from [samples]. */
    fun add(samples: ShortArray, count: Int = samples.size) {
        var i = 0
        while (i + channels <= count) {
            var mono = 0f
            for (c in 0 until channels) mono += samples[i + c]
            sum += mono / (channels * Short.MAX_VALUE.toFloat())
            i += channels
            if (++summed == step) {
                push(sum / step)
                sum = 0f
                summed = 0
            }
        }
    }

    /** The beats of everything added. */
    fun finish(): BeatResult {
        val fps = rate / HOP
        if (frames < fps * MIN_SECONDS) return BeatResult.NONE
        val raw = flux.copyOf(frames)
        val loud = energy.copyOf(frames).sorted()[(frames * 0.95).toInt().coerceAtMost(frames - 1)]
        if (loud < SILENCE) return BeatResult.NONE

        val onset = normalized(raw, (fps * DETREND_SECONDS).roundToInt())
        if (onset.all { it == 0f }) return BeatResult.NONE
        val period = tempoPeriod(smoothed(onset), fps)
        val beats = track(onset, period)
        val (first, last) = activeRange(fps) ?: return BeatResult.NONE
        val kept = beats.filter { it in (first - period / 2).toInt()..(last + period / 2).toInt() }
        if (kept.size < 2) return BeatResult.NONE
        return BeatResult(
            beatsMs = kept.map { frameMs(it) },
            bpm = (60.0 * fps / period).toFloat(),
            strengths = kept.map { t -> (max(0, t - 1)..min(frames - 1, t + 1)).maxOf { onset[it] } },
        )
    }

    private fun push(sample: Float) {
        if (filled < FRAME) {
            window[filled++] = sample
            if (filled == FRAME) frame()
            return
        }
        incoming[pending++] = sample
        if (pending == HOP) {
            System.arraycopy(window, HOP, window, 0, FRAME - HOP)
            System.arraycopy(incoming, 0, window, FRAME - HOP, HOP)
            pending = 0
            frame()
        }
    }

    private fun frame() {
        var squares = 0.0
        for (i in 0 until FRAME) {
            re[i] = window[i] * hann[i]
            im[i] = 0f
            squares += window[i] * window[i]
        }
        fft.transform(re, im)
        var rise = 0f
        for (b in 0 until FRAME / 2) {
            val magnitude = sqrt(re[b] * re[b] + im[b] * im[b]) / FRAME
            current[b] = ln(1f + LOG_GAIN * magnitude)
            if (hasPrevious) rise += max(0f, current[b] - previous[b])
        }
        val swap = previous
        previous = current
        current = swap
        hasPrevious = true
        if (frames == flux.size) {
            flux = flux.copyOf(frames * 2)
            energy = energy.copyOf(frames * 2)
        }
        flux[frames] = rise
        energy[frames] = sqrt(squares / FRAME).toFloat()
        frames++
    }

    /** Frame [t]'s onset time: its flux peaks as a sound crosses the rising quarter of the window. */
    private fun frameMs(t: Int): Long = startMs + ((t * HOP + FRAME / 4 + HOP / 2) * 1000.0 / rate).roundToLong()

    /** Where the music plays: first and last frames with a fair share of the loudest level. */
    private fun activeRange(fps: Double): Pair<Int, Int>? {
        val level = energy.copyOf(frames)
        val threshold = level.sorted()[(frames * 0.95).toInt().coerceAtMost(frames - 1)] * ACTIVE_SHARE
        val first = level.indexOfFirst { it >= threshold }
        val last = level.indexOfLast { it >= threshold }
        return if (first < 0 || last - first < fps * MIN_SECONDS) null else first to last
    }

    private companion object {
        const val TARGET_RATE = 11_025
        const val FRAME = 512
        const val HOP = 128
        const val MIN_BPM = 60.0
        const val MAX_BPM = 200.0
        const val PRIOR_BPM = 120.0
        /** Width of the tempo preference, in octaves. */
        const val PRIOR_OCTAVES = 1.0
        /** How strongly the beats keep to the tempo (Ellis's α, for an onset curve with unit spread). */
        const val TIGHTNESS = 400.0
        const val LOG_GAIN = 1_000f
        const val DETREND_SECONDS = 0.5
        const val MIN_SECONDS = 2.0
        /** RMS of the loud parts below which a track counts as silent (about -54 dBFS). */
        const val SILENCE = 0.002f
        const val ACTIVE_SHARE = 0.1f

        /** The onset curve less its moving average, never negative, divided by its spread. */
        fun normalized(raw: FloatArray, width: Int): FloatArray {
            val half = max(1, width / 2)
            val prefix = DoubleArray(raw.size + 1).also { for (i in raw.indices) it[i + 1] = it[i] + raw[i] }
            val out = FloatArray(raw.size) { t ->
                val from = max(0, t - half)
                val to = min(raw.size, t + half + 1)
                max(0.0, raw[t] - (prefix[to] - prefix[from]) / (to - from)).toFloat()
            }
            val mean = out.average()
            val spread = sqrt(out.sumOf { (it - mean) * (it - mean) } / out.size)
            if (spread <= 1e-9) return FloatArray(raw.size)
            return FloatArray(out.size) { (out[it] / spread).toFloat() }
        }

        /**
         * The onset curve blurred over a few frames (Gaussian, σ = 1 frame), for the tempo: a beat
         * period that isn't a whole number of frames then still lines its peaks up.
         */
        fun smoothed(onset: FloatArray): FloatArray {
            val kernel = floatArrayOf(0.054f, 0.242f, 0.399f, 0.242f, 0.054f)
            return FloatArray(onset.size) { t ->
                var value = 0f
                for (k in kernel.indices) {
                    val i = t + k - kernel.size / 2
                    if (i in onset.indices) value += onset[i] * kernel[k]
                }
                value
            }
        }

        /** The beat period in frames (fractional): the best weighted autocorrelation lag. */
        fun tempoPeriod(onset: FloatArray, fps: Double): Double {
            val minLag = max(1, (60.0 * fps / MAX_BPM).toInt())
            val maxLag = min(onset.size - 1, (60.0 * fps / MIN_BPM).roundToInt() + 1)
            val scores = DoubleArray(maxLag + 2)
            for (lag in minLag..maxLag) {
                var ac = 0.0
                for (t in 0 until onset.size - lag) ac += onset[t] * onset[t + lag]
                ac /= (onset.size - lag)
                val bpm = 60.0 * fps / lag
                val prior = exp(-0.5 * (log2(bpm / PRIOR_BPM) / PRIOR_OCTAVES).let { it * it })
                scores[lag] = ac * prior
            }
            val best = (minLag..maxLag).maxBy { scores[it] }
            // Parabolic interpolation between the neighbouring lags.
            if (best <= minLag || best >= maxLag) return best.toDouble()
            val (a, b, c) = Triple(scores[best - 1], scores[best], scores[best + 1])
            val denominator = a - 2 * b + c
            val shift = if (abs(denominator) < 1e-12) 0.0 else (0.5 * (a - c) / denominator).coerceIn(-0.5, 0.5)
            return best + shift
        }

        /** Ellis's dynamic programming: the best-scoring beat frames, ascending. */
        fun track(onset: FloatArray, period: Double): List<Int> {
            val n = onset.size
            val score = DoubleArray(n)
            val back = IntArray(n) { -1 }
            val from = max(1, (period / 2).roundToInt())
            val to = max(from, (period * 2).roundToInt())
            for (t in 0 until n) {
                var bestPrevious = Double.NEGATIVE_INFINITY
                var bestIndex = -1
                for (gap in from..to) {
                    val previous = t - gap
                    if (previous < 0) break
                    val drift = ln(gap / period)
                    val value = score[previous] - TIGHTNESS * drift * drift
                    if (value > bestPrevious) {
                        bestPrevious = value
                        bestIndex = previous
                    }
                }
                score[t] = onset[t] + if (bestIndex >= 0) max(0.0, bestPrevious) else 0.0
                back[t] = if (bestIndex >= 0 && bestPrevious > 0) bestIndex else -1
            }
            // The last beat: the best score within the final period.
            var t = (max(0, n - period.roundToInt())..<n).maxBy { score[it] }
            val beats = ArrayList<Int>()
            while (t >= 0) {
                beats += t
                t = back[t]
            }
            return beats.asReversed()
        }
    }
}

/** An in-place radix-2 FFT of a fixed power-of-two [size]. */
internal class Fft(private val size: Int) {
    private val levels = Integer.numberOfTrailingZeros(size)
    private val cosTable = FloatArray(size / 2) { cos(2 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2 * PI * it / size).toFloat() }
    private val reversed = IntArray(size) { Integer.reverse(it) ushr (32 - levels) }

    init {
        require(size >= 2 && size and (size - 1) == 0) { "size must be a power of two" }
    }

    fun transform(re: FloatArray, im: FloatArray) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var half = 1
        while (half < size) {
            val tableStep = size / (half * 2)
            var start = 0
            while (start < size) {
                for (k in 0 until half) {
                    val c = cosTable[k * tableStep]
                    val s = sinTable[k * tableStep]
                    val a = start + k
                    val b = a + half
                    val tre = re[b] * c + im[b] * s
                    val tim = -re[b] * s + im[b] * c
                    re[b] = re[a] - tre
                    im[b] = im[a] - tim
                    re[a] += tre
                    im[a] += tim
                }
                start += half * 2
            }
            half *= 2
        }
    }
}
