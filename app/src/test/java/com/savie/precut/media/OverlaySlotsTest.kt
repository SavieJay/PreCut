package com.savie.precut.media

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlaySlotsTest {

    @Test
    fun `back-to-back captions all share slot 0`() {
        val captions = (0 until 200).map { (it * 2_000_000L) until ((it + 1) * 2_000_000L) }
        assertEquals(List(200) { 0 }, overlaySlots(captions))
    }

    @Test
    fun `captions after a full-length title go in the slot above it`() {
        val title = 0L until 100_000_000L
        val captions = (0 until 50).map { (it * 2_000_000L) until ((it + 1) * 2_000_000L) }
        assertEquals(listOf(0) + List(50) { 1 }, overlaySlots(listOf(title) + captions))
    }

    @Test
    fun `texts on screen together each get their own slot, in order`() {
        assertEquals(listOf(0, 1, 2), overlaySlots(listOf(0L until 10L, 2L until 8L, 4L until 6L)))
    }

    @Test
    fun `a later text overlapping only a text in slot 1 goes above it`() {
        // B overlaps A (slot 1); C overlaps only B, so it must draw above B.
        val ranges = listOf(0L until 10L, 5L until 20L, 15L until 25L)
        assertEquals(listOf(0, 1, 2), overlaySlots(ranges))
    }

    @Test
    fun `a later text overlapping nothing goes back to slot 0`() {
        assertEquals(listOf(0, 1, 0), overlaySlots(listOf(0L until 10L, 5L until 10L, 10L until 20L)))
    }

    @Test
    fun `touching ranges don't overlap`() {
        assertEquals(listOf(0, 0), overlaySlots(listOf(0L until 10L, 10L until 20L)))
    }

    @Test
    fun `no texts, no slots`() {
        assertEquals(emptyList<Int>(), overlaySlots(emptyList()))
    }

    @Test
    fun `overlays are split into effects of at most 15`() {
        assertEquals(listOf(15, 15, 10), overlayChunks((1..40).toList()).map { it.size })
        assertEquals(listOf(15), overlayChunks((1..15).toList()).map { it.size })
    }
}
