package com.savie.precut.model.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPartsTest {
    private val minute = 60_000L

    @Test
    fun shortSound_isOnePart() {
        assertEquals(listOf(SpeechPart(0L, 4 * minute)), SpeechParts.plan(4 * minute, 10 * minute))
        assertTrue(SpeechParts.plan(0L, 10 * minute).isEmpty())
    }

    @Test
    fun longSound_withoutLevels_isCutAtTheLimit() {
        val parts = SpeechParts.plan(25 * minute, 10 * minute)
        assertEquals(listOf(SpeechPart(0L, 10 * minute), SpeechPart(10 * minute, 20 * minute), SpeechPart(20 * minute, 25 * minute)), parts)
    }

    @Test
    fun cutsFallAtTheQuietestMomentBeforeTheLimit() {
        val window = 50L
        val levels = FloatArray((25 * minute / window).toInt()) { 0.8f }
        // A pause 12 s before the first limit, and a quieter one too far back to be chosen.
        val pause = ((10 * minute - 12_000L) / window).toInt()
        levels[pause] = 0.01f
        levels[((10 * minute - 45_000L) / window).toInt()] = 0f
        val parts = SpeechParts.plan(25 * minute, 10 * minute, levels, window)
        assertEquals(pause * window + window / 2, parts[0].endMs)
        assertEquals(parts[0].endMs, parts[1].startMs)
        assertTrue(parts.all { it.durationMs <= 10 * minute })
        assertEquals(25 * minute, parts.last().endMs)
    }

    @Test
    fun partsCoverEverythingAndNeverPassTheLimit() {
        val levels = FloatArray(30 * 60 * 20) { (it * 7919 % 100) / 100f }
        val parts = SpeechParts.plan(29 * minute + 1_234L, 9 * minute + 58_000L, levels, 50L)
        assertEquals(0L, parts.first().startMs)
        assertEquals(29 * minute + 1_234L, parts.last().endMs)
        parts.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        assertTrue(parts.all { it.durationMs in 1..(9 * minute + 58_000L) })
    }

    @Test
    fun chargedSeconds_roundEachPartUp() {
        assertEquals(3, SpeechParts.chargedSeconds(listOf(SpeechPart(0L, 1_001L), SpeechPart(1_001L, 2_000L))))
    }

    @Test
    fun affordable_keepsTheLeadingPartsThatFit() {
        val parts = SpeechParts.plan(25 * minute, 10 * minute)
        assertEquals(parts.take(2), SpeechParts.affordable(parts, 22 * 60))
        assertEquals(parts, SpeechParts.affordable(parts, 25 * 60))
        assertTrue(SpeechParts.affordable(parts, 9 * 60).isEmpty())
    }
}
