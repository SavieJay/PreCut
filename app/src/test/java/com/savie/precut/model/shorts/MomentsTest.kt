package com.savie.precut.model.shorts

import com.savie.precut.model.edit.SpokenWord
import com.savie.precut.model.project
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentsTest {
    /** Two minutes, quiet except a loud stretch from 60 to 90 s. */
    private val energy = List(120) { second -> if (second in 60 until 90) 1f else 0.1f }

    @Test
    fun `the loud stretch wins, moments don't overlap and fit the length`() {
        val moments = findMoments(MomentSignals(120_000L, energy, sceneCutsMs = emptyList()), count = 3)
        assertEquals(3, moments.size)
        val best = moments.first()
        assertTrue(best.startMs >= 55_000L && best.endMs <= 95_000L)
        assertTrue(moments.all { it.lengthMs in MIN_SHORT_MS..MAX_SHORT_MS })
        moments.forEachIndexed { i, a -> moments.drop(i + 1).forEach { b -> assertTrue(a.endMs <= b.startMs || b.endMs <= a.startMs) } }
    }

    @Test
    fun `with captions, moments start and end on lines, titled by their first line`() {
        val lines = (0 until 12).map { i -> Line(i * 10_000L, i * 10_000L + 9_000L, if (i == 6) "Did you see that?" else "Line $i") }
        val words = lines.flatMap { line -> (0 until 20).map { SpokenWord("w", line.startMs + it * 400L, line.startMs + it * 400L + 300L, "c") } }
        val moments = findMoments(MomentSignals(120_000L, energy, emptyList(), words, lines), count = 2)
        val best = moments.first()
        assertTrue(lines.any { it.startMs == best.startMs })
        assertTrue(lines.any { it.endMs == best.endMs })
        assertEquals("Did you see that?", best.title)
        assertTrue(best.hookScore > 0)
    }

    @Test
    fun `too short for shorts gives nothing, and a short keeps only its moment`() {
        assertTrue(findMoments(MomentSignals(10_000L, List(10) { 1f }, emptyList())).isEmpty())
        val long = project(videoClip("v", sourceDurationMs = 120_000L))
        val short = long.shortOf(Moment(60_000L, 90_000L, "", 0))
        assertEquals(30_000L, short.totalDurationMs())
        assertEquals(60_000L, short.clips.single().trimStartMs)
    }

    @Test
    fun `a jump in the colour layout is a scene cut`() {
        val dark = FloatArray(48) { 0.1f }
        val bright = FloatArray(48) { 0.8f }
        assertEquals(listOf(2_000L), sceneCuts(listOf(0L, 1_000L, 2_000L, 3_000L), listOf(dark, dark, bright, bright)))
    }
}
