package com.savie.precut.model.text

import com.savie.precut.model.TextOverlay
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.project
import com.savie.precut.model.validSpans
import com.savie.precut.model.videoClip
import com.savie.precut.model.withSpanStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextTest {
    private val pink = 0xFFFF4D6D.toInt()
    private val text = TextOverlay("t", "Hello PreCut", 0L, 3_000L)

    @Test
    fun withSpanStyle_marksTheRange() {
        val styled = text.withSpanStyle(0, 5, TextSpan(0, 0, colorArgb = pink))
        assertEquals(listOf(TextSpan(0, 5, colorArgb = pink)), styled.spans)
    }

    @Test
    fun withSpanStyle_splitsAndMergesOverlaps() {
        val styled = text
            .withSpanStyle(0, 5, TextSpan(0, 0, colorArgb = pink))
            .withSpanStyle(3, 8, TextSpan(0, 0, bold = true))
        assertEquals(
            listOf(
                TextSpan(0, 3, colorArgb = pink),
                TextSpan(3, 5, colorArgb = pink, bold = true),
                TextSpan(5, 8, bold = true),
            ),
            styled.spans,
        )
    }

    @Test
    fun withSpanStyle_ignoresEmptyRanges() {
        assertEquals(text, text.withSpanStyle(4, 4, TextSpan(0, 0, bold = true)))
    }

    @Test
    fun validSpans_followShorterWords() {
        val styled = text.copy(spans = listOf(TextSpan(0, 5, bold = true), TextSpan(6, 12, italic = true)))
        assertEquals(listOf(TextSpan(0, 5, bold = true), TextSpan(6, 7, italic = true)), styled.copy(text = "Hello P").validSpans())
        assertEquals(emptyList<TextSpan>(), styled.copy(text = "").validSpans())
    }

    @Test
    fun updateText_dropsSpansPastTheWords() {
        val p = project(videoClip()).copy(textOverlays = listOf(text.copy(spans = listOf(TextSpan(6, 12, colorArgb = pink)))))
        val edited = EditReducer.reduce(p, EditAction.UpdateText(p.textOverlays.single().copy(text = "Hello")))
        assertEquals("Hello", edited.textOverlays.single().text)
        assertEquals(emptyList<TextSpan>(), edited.textOverlays.single().spans)
    }
}
