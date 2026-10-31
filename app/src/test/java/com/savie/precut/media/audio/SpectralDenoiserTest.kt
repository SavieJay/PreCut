package com.savie.precut.media.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class SpectralDenoiserTest {

    private val rate = 48_000

    private fun noise(frames: Int, channels: Int, level: Float, seed: Int = 1): FloatArray {
        val random = Random(seed)
        return FloatArray(frames * channels) { (random.nextFloat() * 2f - 1f) * level }
    }

    private fun tone(frames: Int, channels: Int, hz: Float, level: Float) =
        FloatArray(frames * channels) { i -> (sin(2 * PI * hz * (i / channels) / rate) * level).toFloat() }

    private fun rms(samples: FloatArray, from: Int = 0, to: Int = samples.size): Double =
        sqrt((from until to).sumOf { samples[it].toDouble() * samples[it] } / (to - from))

    private fun profileOf(samples: FloatArray): List<Float> =
        NoiseProfileBuilder(rate).apply { add(samples, samples.size) }.build()!!

    /** Runs [input] through a denoiser in chunks of [chunks] frames (cycled), then finishes it. */
    private fun run(denoiser: SpectralDenoiser, input: FloatArray, channels: Int, chunks: IntArray = intArrayOf(4096)): FloatArray {
        val out = ArrayList<Float>(input.size)
        var at = 0
        var i = 0
        while (at < input.size / channels) {
            val frames = minOf(chunks[i++ % chunks.size], input.size / channels - at)
            denoiser.process(input.copyOfRange(at * channels, (at + frames) * channels), frames)
            denoiser.drain { samples, count -> for (s in 0 until count * channels) out += samples[s] }
            at += frames
        }
        denoiser.finish()
        denoiser.drain { samples, count -> for (s in 0 until count * channels) out += samples[s] }
        return out.toFloatArray()
    }

    @Test
    fun fft_roundTrips() {
        val fft = Fft(256)
        val random = Random(7)
        val re = FloatArray(256) { random.nextFloat() }
        val im = FloatArray(256)
        val original = re.copyOf()
        fft.forward(re, im)
        // Bin 0 is the plain sum.
        assertEquals(original.sum(), re[0], 1e-3f)
        fft.inverse(re, im)
        for (i in 0 until 256) assertEquals(original[i], re[i], 1e-5f)
    }

    @Test
    fun strengthZero_givesTheSoundBackExactly_withNothingAddedOrLost() {
        val input = noise(10_007, channels = 2, level = 0.5f)
        val output = run(SpectralDenoiser(rate, 2, List(NoiseBands.COUNT) { 1f }, strength = 0f), input, 2, intArrayOf(313, 1024, 7))
        assertEquals(input.size, output.size)
        for (i in input.indices) assertEquals(input[i], output[i], 1e-4f)
    }

    @Test
    fun noDelay_soPictureAndSoundStayTogether() {
        val input = FloatArray(20_000).also { it[12_345] = 0.9f }
        val quiet = profileOf(noise(48_000, 1, 0.001f))
        val output = run(SpectralDenoiser(rate, 1, quiet, strength = 0.5f), input, 1)
        assertEquals(input.size, output.size)
        assertEquals(12_345, output.indices.maxByOrNull { abs(output[it]) })
    }

    @Test
    fun chunkSizes_dontChangeTheResult() {
        val input = noise(30_000, 2, 0.2f, seed = 3)
        val profile = profileOf(noise(48_000, 1, 0.05f))
        val whole = run(SpectralDenoiser(rate, 2, profile, 0.7f), input, 2, intArrayOf(30_000))
        val pieces = run(SpectralDenoiser(rate, 2, profile, 0.7f), input, 2, intArrayOf(1, 999, 64, 4_000))
        assertEquals(whole.toList(), pieces.toList())
    }

    @Test
    fun steadyNoise_isTakenDown_whileAToneStays() {
        val noiseLevel = 0.05f
        val profile = profileOf(noise(48_000, 1, noiseLevel, seed = 11))
        val denoiser = { SpectralDenoiser(rate, 1, profile, strength = 0.8f) }
        // Noise alone: at least 10 dB quieter (past the start-up).
        val hiss = noise(96_000, 1, noiseLevel, seed = 12)
        val cleaned = run(denoiser(), hiss, 1)
        val drop = 20 * log10(rms(hiss, 10_000, 96_000) / rms(cleaned, 10_000, 96_000))
        assertTrue("only $drop dB", drop >= 10.0)
        // A clear tone well above the noise: within 1 dB.
        val voice = tone(96_000, 1, 440f, 0.3f)
        val kept = run(denoiser(), voice, 1)
        val change = 20 * log10(rms(kept, 10_000, 96_000) / rms(voice, 10_000, 96_000))
        assertTrue("changed $change dB", abs(change) <= 1.0)
    }

    @Test
    fun reset_startsAfresh() {
        val input = noise(9_000, 1, 0.3f)
        val profile = profileOf(noise(48_000, 1, 0.05f))
        val fresh = run(SpectralDenoiser(rate, 1, profile, 0.6f), input, 1)
        val used = SpectralDenoiser(rate, 1, profile, 0.6f).also { run(it, noise(5_000, 1, 0.4f, seed = 9), 1); it.reset() }
        assertEquals(fresh.toList(), run(used, input, 1).toList())
    }

    @Test
    fun profile_measuresTheNoiseFloor_andNeedsSound() {
        val level = 0.1f
        val profile = profileOf(noise(240_000, 1, level))
        // Uniform noise of ±0.1 has variance 0.1² / 3 in every band, the narrow low ones included.
        val variance = level * level / 3f
        assertEquals(NoiseBands.COUNT, profile.size)
        profile.forEachIndexed { band, value -> assertTrue("band $band: $value", value in variance * 0.5f..variance * 1.6f) }
        assertNull(NoiseProfileBuilder(rate).apply { add(FloatArray(48_000), 48_000) }.build())
        assertNull(NoiseProfileBuilder(rate).apply { add(noise(2_000, 1, 0.1f), 2_000) }.build())
    }

    @Test
    fun profile_isTheNoise_notWhatComesAndGoes() {
        // A loud tone every other half second over steady noise: the pauses give the noise.
        val level = 0.05f
        val hiss = noise(240_000, 1, level, seed = 5)
        val tone = tone(240_000, 1, 440f, 0.3f)
        val mixed = FloatArray(hiss.size) { i -> hiss[i] + if ((i / 24_000) % 2 == 0) tone[i] else 0f }
        val profile = profileOf(mixed)
        val band = NoiseBands.of(440f)
        assertTrue("440 Hz band: ${profile[band]}", profile[band] < 2f * level * level / 3f)
    }

    @Test
    fun bands_andGains() {
        assertEquals(0, NoiseBands.of(50f))
        assertEquals(1, NoiseBands.of(100f))
        assertEquals(NoiseBands.COUNT - 1, NoiseBands.of(24_000f))
        assertEquals(NoiseBands.COUNT - 1, NoiseBands.of(40_000f))
        assertTrue((100..24_000 step 50).map { NoiseBands.of(it.toFloat()) }.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(1f, SpectralDenoiser.gainFor(1f, 0f, 2f, 0.1f), 0f)
        assertEquals(1f, SpectralDenoiser.gainFor(1e6f, 1f, 2f, 0.1f), 1e-3f)
        assertEquals(0.1f, SpectralDenoiser.gainFor(1f, 1f, 2f, 0.1f), 1e-6f)
        assertEquals(512, Fft.sizeFor(16_000))
        assertEquals(1024, Fft.sizeFor(44_100))
        assertEquals(1024, Fft.sizeFor(48_000))
    }
}
