package com.savie.precut.model.text

import com.savie.precut.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SrtTest {

    @Test
    fun captionsInTimeOrder_numberedFromOne() {
        val texts = listOf(
            TextOverlay("2", "Second line", 3_723_456L, 3_725_000L, caption = CaptionInfo("g")),
            TextOverlay("t", "A title", 0L, 1_000L),
            TextOverlay("1", "First\n\nline", 1_250L, 2_000L, caption = CaptionInfo("g")),
            TextOverlay("blank", "  ", 2_000L, 2_500L, caption = CaptionInfo("g")),
        )
        val expected = "1\n00:00:01,250 --> 00:00:02,000\nFirst\nline\n\n" +
            "2\n01:02:03,456 --> 01:02:05,000\nSecond line\n\n"
        assertEquals(expected, Srt.of(texts))
    }

    @Test
    fun noCaptions_noFile() {
        assertNull(Srt.of(listOf(TextOverlay("t", "A title", 0L, 1_000L))))
    }

    @Test
    fun fileName_isSafe() {
        assertEquals("Trip_ day 1.srt", Srt.fileName("Trip: day 1"))
        assertEquals("Captions.srt", Srt.fileName("  "))
        assertEquals("a_b.srt", Srt.fileName("a/b"))
    }
}
