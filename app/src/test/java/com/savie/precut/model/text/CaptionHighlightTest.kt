package com.savie.precut.model.text

import com.savie.precut.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionHighlightTest {
    private val yellow = 0xFFFFD43B.toInt()
    private val words = listOf(CaptionWord("the", 0L, 200L), CaptionWord("cat", 300L, 500L), CaptionWord("the", 700L, 900L))
    private val info = CaptionInfo("g", words, highlightArgb = yellow)
    private val caption = TextOverlay("c", "the cat the", 1_000L, 2_000L, caption = info)

    @Test
    fun wordAt_isTheLastWordStarted() {
        assertNull(info.copy(words = listOf(CaptionWord("late", 100L, 300L))).wordAt(50L))
        assertEquals(0, info.wordAt(0L))
        assertEquals(0, info.wordAt(250L)) // a gap keeps the word just spoken
        assertEquals(1, info.wordAt(300L))
        assertEquals(2, info.wordAt(5_000L))
    }

    @Test
    fun wordRanges_followTheWordsInOrder() {
        assertEquals(listOf(0 until 3, 4 until 7, 8 until 11), info.wordRanges("the cat the"))
    }

    @Test
    fun wordRanges_missAnEditedWord() {
        assertEquals(listOf(0 until 3, null, 8 until 11), info.wordRanges("the dog the"))
    }

    @Test
    fun withWordHighlighted_coloursThatWordOnly() {
        assertEquals(listOf(TextSpan(4, 7, colorArgb = yellow)), caption.withWordHighlighted(1).spans)
        assertEquals(listOf(TextSpan(8, 11, colorArgb = yellow)), caption.withWordHighlighted(2).spans)
    }

    @Test
    fun noHighlightColour_noHighlight() {
        val plain = caption.copy(caption = info.copy(highlightArgb = null))
        assertNull(plain.highlightedWordAt(400L))
        assertTrue(plain.withWordHighlighted(1) === plain)
        assertEquals(1, caption.highlightedWordAt(400L))
    }

    @Test
    fun highlightPresetsSetTheColour() {
        val styled = CaptionStyle.HIGHLIGHT.applyTo(caption.copy(caption = info.copy(highlightArgb = null)))
        assertEquals(yellow, styled.caption?.highlightArgb)
        assertNull(CaptionStyle.CLASSIC.applyTo(caption).caption?.highlightArgb)
    }
}
