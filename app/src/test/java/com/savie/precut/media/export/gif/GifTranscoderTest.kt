package com.savie.precut.media.export.gif

import org.junit.Assert.assertEquals
import org.junit.Test

class GifTranscoderTest {

    @Test
    fun `the last frame lasts until the project's end`() {
        // 15 fps GIF of an 11 s project whose last kept frame is at 10.933 s.
        assertEquals(67L, GifTranscoder.lastFrameMs(frameUs = 10_933_333, endMs = 11_000, frameMs = 66))
        // A video track ending 1.5 frames early still reaches the end.
        assertEquals(100L, GifTranscoder.lastFrameMs(frameUs = 10_900_000, endMs = 11_000, frameMs = 66))
    }

    @Test
    fun `the last frame never lasts less than one frame`() {
        assertEquals(66L, GifTranscoder.lastFrameMs(frameUs = 11_000_000, endMs = 11_000, frameMs = 66))
        assertEquals(66L, GifTranscoder.lastFrameMs(frameUs = 10_990_000, endMs = 11_000, frameMs = 66))
    }
}
