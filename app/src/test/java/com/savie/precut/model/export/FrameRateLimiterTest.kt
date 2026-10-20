package com.savie.precut.model.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

class FrameRateLimiterTest {

    /** Timestamps of [count] frames at [fps], rounded to whole microseconds like real streams. */
    private fun frames(fps: Double, count: Int, startUs: Long = 0) = List(count) { startUs + (it * 1_000_000 / fps).roundToLong() }

    private fun kept(limiter: FrameRateLimiter, times: List<Long>) = times.filter { limiter.keep(it) }

    @Test
    fun `a stream at the target rate keeps every frame`() {
        val times = frames(30.0, 300)
        assertEquals(times, kept(FrameRateLimiter(30), times))
    }

    @Test
    fun `a stream under the target rate keeps every frame`() {
        val times = frames(24.0, 240)
        assertEquals(times, kept(FrameRateLimiter(30), times))
    }

    @Test
    fun `NTSC 29_97 at 30 keeps every frame`() {
        val times = frames(30_000 / 1_001.0, 300)
        assertEquals(times, kept(FrameRateLimiter(30), times))
    }

    @Test
    fun `phone timing jitter at the target rate keeps every frame`() {
        val jitter = listOf(0L, 2_000L, -2_000L, 1_500L, -1_000L)
        val times = frames(30.0, 300).mapIndexed { i, t -> t + if (i == 0) 0 else jitter[i % jitter.size] }
        assertEquals(times, kept(FrameRateLimiter(30), times))
    }

    @Test
    fun `60 to 30 keeps every other frame`() {
        val times = frames(60.0, 600)
        assertEquals(times.filterIndexed { i, _ -> i % 2 == 0 }, kept(FrameRateLimiter(30), times))
    }

    @Test
    fun `30 to 24 keeps four in five, evenly`() {
        val kept = kept(FrameRateLimiter(24), frames(30.0, 300))
        assertEquals(240, kept.size)
        val gaps = kept.zipWithNext { a, b -> b - a }.toSet()
        assertTrue("gaps $gaps", gaps.all { it in 33_000L..67_000L })
    }

    @Test
    fun `never more frames than the target rate allows`() {
        for (source in listOf(25.0, 50.0, 59.94, 60.0, 120.0)) for (target in listOf(24, 25, 30, 50, 60)) {
            val times = frames(source, (source * 10).toInt())
            val kept = kept(FrameRateLimiter(target), times)
            assertTrue("$source → $target kept ${kept.size}", kept.size <= target * 10 + 1)
            if (source <= target) assertEquals("$source → $target", times.size, kept.size)
        }
    }

    @Test
    fun `cuts between items lose no frame`() {
        // Two items placed back to back on the composition timeline, as Transformer offsets them.
        val times = frames(30.0, 90) + frames(30.0, 90, startUs = 3_000_000)
        assertEquals(times, kept(FrameRateLimiter(30), times))
    }

    @Test
    fun `a jump back starts over`() {
        val limiter = FrameRateLimiter(30)
        kept(limiter, frames(60.0, 60))
        assertTrue(limiter.keep(0))
        assertEquals(false, limiter.keep(16_667))
        assertTrue(limiter.keep(33_333))
    }
}
