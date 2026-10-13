package com.savie.precut.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioPlacementTest {

    private fun audio(start: Long = 0L, trimStart: Long = 0L, trimEnd: Long = 30_000L, speed: Float = 1f) = AudioClip(
        "m", "content://m", sourceDurationMs = 60_000L, timelineStartMs = start, trimStartMs = trimStart, trimEndMs = trimEnd, speed = speed,
    )

    @Test
    fun shorterAudio_playsFullyAndEndsEarly() {
        assertEquals(AudioPlacement(2_000L, 30_000L, 0L, 30_000L), audio(start = 2_000L).placementWithin(60_000L))
    }

    @Test
    fun longerAudio_isCutAtVideoEnd() {
        assertEquals(AudioPlacement(5_000L, 15_000L, 0L, 15_000L), audio(start = 5_000L).placementWithin(20_000L))
    }

    @Test
    fun speed_scalesTimelineLength_andTheCut() {
        // 30 s of source at 2x = 15 s on the timeline.
        assertEquals(15_000L, audio(speed = 2f).timelineDurationMs())
        // Cut after 10 s of timeline = 20 s of source.
        assertEquals(AudioPlacement(0L, 10_000L, 0L, 20_000L), audio(speed = 2f).placementWithin(10_000L))
    }

    @Test
    fun trimmedAudio_cutsFromItsTrimStart() {
        assertEquals(AudioPlacement(0L, 10_000L, 10_000L, 20_000L), audio(trimStart = 10_000L, trimEnd = 40_000L).placementWithin(10_000L))
    }

    @Test
    fun audioStartingAtOrAfterVideoEnd_doesNotPlay() {
        assertNull(audio(start = 20_000L).placementWithin(20_000L))
        assertNull(audio().placementWithin(0L))
    }

    @Test
    fun sourceAndTimelineMapping_withSpeed() {
        val a = audio(start = 1_000L, trimStart = 4_000L, speed = 2f)
        assertEquals(8_000L, a.sourceAt(3_000L))
        assertEquals(3_000L, a.timelineAt(8_000L))
    }
}
