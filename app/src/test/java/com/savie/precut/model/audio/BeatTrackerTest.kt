package com.savie.precut.model.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class BeatTrackerTest {

    private val rate = 44_100

    /** Stereo 16-bit audio of [seconds], with [sound] (−1..1) at each time in seconds. */
    private fun render(seconds: Double, sound: (Double) -> Double): ShortArray {
        val frames = (seconds * rate).toInt()
        return ShortArray(frames * 2) { i -> (sound((i / 2).toDouble() / rate).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
    }

    /** Fed in uneven chunks, as a decoder would. */
    private fun detect(audio: ShortArray, startMs: Long = 0): BeatResult {
        val tracker = BeatTracker(rate, channels = 2, startMs = startMs)
        var at = 0
        val sizes = intArrayOf(4096, 2048, 8192, 1024)
        var k = 0
        while (at < audio.size) {
            val chunk = audio.copyOfRange(at, minOf(audio.size, at + sizes[k++ % sizes.size]))
            tracker.add(chunk)
            at += chunk.size
        }
        return tracker.finish()
    }

    private fun clickTrack(bpm: Double, offset: Double, seconds: Double, noise: Double = 0.02): Pair<ShortArray, List<Double>> {
        val beat = 60.0 / bpm
        val clicks = generateSequence(offset) { it + beat }.takeWhile { it < seconds - 0.1 }.toList()
        val random = Random(7)
        val audio = render(seconds) { t ->
            val since = (t - offset).let { if (it < 0) -1.0 else it % beat }
            val click = if (since in 0.0..0.03) 0.8 * exp(-since * 120) * sin(2 * PI * 1_000 * since) else 0.0
            click + noise * (random.nextDouble() * 2 - 1)
        }
        return audio to clicks
    }

    /** Share of [truth] times (after the first two seconds) with a detected beat within [toleranceMs]. */
    private fun hitRate(beats: List<Long>, truth: List<Double>, toleranceMs: Long): Double {
        val checked = truth.filter { it > 2.0 }
        return checked.count { t -> beats.any { abs(it - t * 1000) <= toleranceMs } }.toDouble() / checked.size
    }

    @Test
    fun `click tracks give their tempo and beats`() {
        for (bpm in listOf(90.0, 120.0, 128.0, 150.0)) {
            val (audio, clicks) = clickTrack(bpm, offset = 0.37, seconds = 20.0)
            val result = detect(audio)
            assertEquals("tempo at $bpm", bpm, result.bpm.toDouble(), bpm * 0.02)
            val hits = hitRate(result.beatsMs, clicks, toleranceMs = 25)
            assertTrue("$bpm BPM: ${(hits * 100).roundToInt()}% of beats within 25 ms", hits >= 0.9)
        }
    }

    @Test
    fun `beats are reported in the track's own time`() {
        val (audio, clicks) = clickTrack(120.0, offset = 0.5, seconds = 12.0)
        val shifted = detect(audio, startMs = 1_000).beatsMs.map { it - 1_000 }
        assertTrue(hitRate(shifted, clicks, toleranceMs = 25) >= 0.9)
    }

    @Test
    fun `off-beat hi-hats don't double the tempo`() {
        val bpm = 100.0
        val beat = 60.0 / bpm
        val random = Random(3)
        val audio = render(20.0) { t ->
            val sinceKick = t % beat
            val sinceHat = t % (beat / 2)
            val kick = if (sinceKick < 0.2) 0.9 * exp(-sinceKick * 25) * sin(2 * PI * 60 * sinceKick) else 0.0
            val hat = if (sinceHat < 0.02) 0.25 * exp(-sinceHat * 200) * (random.nextDouble() * 2 - 1) else 0.0
            kick + hat + 0.1 * sin(2 * PI * 220 * t)
        }
        val result = detect(audio)
        assertEquals(bpm, result.bpm.toDouble(), bpm * 0.03)
        val kicks = generateSequence(0.0) { it + beat }.takeWhile { it < 19.9 }.toList()
        assertTrue(hitRate(result.beatsMs, kicks, toleranceMs = 30) >= 0.9)
    }

    @Test
    fun `silence has no beats`() {
        assertEquals(BeatResult.NONE, detect(ShortArray(rate * 2 * 10)))
    }

    @Test
    fun `too short to tell has no beats`() {
        assertEquals(BeatResult.NONE, detect(clickTrack(120.0, 0.1, seconds = 1.0).first))
    }

    @Test
    fun `plain noise doesn't crash and gives ordered beats`() {
        val random = Random(11)
        val result = detect(render(10.0) { 0.3 * (random.nextDouble() * 2 - 1) })
        assertEquals(result.beatsMs.sorted(), result.beatsMs)
        assertEquals(result.beatsMs.size, result.strengths.size)
    }

    @Test
    fun `every second or fourth beat keeps the strongest set`() {
        val beats = (0 until 12).map { it * 500L }
        // Strong on beats 1, 5, 9 (index 1 mod 4): bar starts.
        val strengths = beats.indices.map { if (it % 4 == 1) 5f else 1f }
        val result = BeatResult(beats, 120f, strengths)
        assertEquals(listOf(500L, 2_500L, 4_500L), result.every(4))
        assertEquals(listOf(500L, 1_500L, 2_500L, 3_500L, 4_500L, 5_500L), result.every(2))
        assertEquals(beats, result.every(1))
    }
}
