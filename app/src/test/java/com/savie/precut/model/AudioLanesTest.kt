package com.savie.precut.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioLanesTest {

    private fun audio(id: String, start: Long, length: Long) =
        AudioClip(id, "content://$id", sourceDurationMs = 60_000L, timelineStartMs = start, trimEndMs = length)

    private fun lanes(vararg clips: AudioClip) =
        project().copy(audioClips = clips.toList()).audioLanes().map { lane -> lane.map { it.id } }

    @Test
    fun nonOverlapping_shareOneRow_inStartOrder() {
        assertEquals(listOf(listOf("a", "b")), lanes(audio("b", 5_000L, 2_000L), audio("a", 0L, 3_000L)))
    }

    @Test
    fun touchingEdges_shareARow() {
        assertEquals(listOf(listOf("a", "b")), lanes(audio("a", 0L, 3_000L), audio("b", 3_000L, 1_000L)))
    }

    @Test
    fun overlaps_stackOntoNewRows_andReuseFreedRows() {
        val result = lanes(
            audio("a", 0L, 10_000L),
            audio("b", 2_000L, 3_000L), // overlaps a -> row 2
            audio("c", 4_000L, 1_000L), // overlaps a and b -> row 3
            audio("d", 6_000L, 1_000L), // b ended at 5 s -> row 2
        )
        assertEquals(listOf(listOf("a"), listOf("b", "d"), listOf("c")), result)
    }

    @Test
    fun ties_breakById_soLanesAreStable() {
        assertEquals(listOf(listOf("a"), listOf("b")), lanes(audio("b", 0L, 1_000L), audio("a", 0L, 1_000L)))
    }

    @Test
    fun empty_hasNoRows() {
        assertEquals(emptyList<List<String>>(), lanes())
    }
}
