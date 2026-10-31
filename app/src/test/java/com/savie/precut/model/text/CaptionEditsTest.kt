package com.savie.precut.model.text

import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.ai.TranscriptWord
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.edit.coalesceKeyFor
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionEditsTest {

    /** "one two three", 1.0–2.5 s, words at 0–300, 400–700 and 900–1200 ms into it. */
    private val line = TextOverlay(
        "a", "one two three", 1_000L, 2_500L,
        caption = CaptionInfo("g", listOf(CaptionWord("one", 0L, 300L), CaptionWord("two", 400L, 700L), CaptionWord("three", 900L, 1_200L)), styleId = "classic"),
    )
    private val next = TextOverlay("b", "four", 3_000L, 3_600L, caption = CaptionInfo("g", listOf(CaptionWord("four", 100L, 500L))))
    private val title = TextOverlay("t", "Title", 0L, 1_000L)
    private val base: Project = project(videoClip()).copy(textOverlays = listOf(title, line, next))

    private fun reduce(action: EditAction) = EditReducer.reduce(base, action)
    private fun Project.text(id: String) = textOverlays.single { it.id == id }

    @Test
    fun spreadWords_byLength() {
        // "a bbb": 2 + 4 units over 600 ms.
        assertEquals(listOf(CaptionWord("a", 0L, 100L), CaptionWord("bbb", 200L, 500L)), spreadWords(" a  bbb ", 600L))
        assertTrue(spreadWords("   ", 600L).isEmpty())
    }

    @Test
    fun setCaptionText_keepsTimesWhenOnlySpacingChanged_elseSpreads() {
        assertEquals(line.caption?.words, reduce(EditAction.SetCaptionText("a", "one  two three")).text("a").caption?.words)
        val edited = reduce(EditAction.SetCaptionText("a", "one 2 three four")).text("a")
        assertEquals("one 2 three four", edited.text)
        assertEquals(listOf("one", "2", "three", "four"), edited.caption?.words?.map { it.text })
        assertEquals(0L, edited.caption?.words?.first()?.startMs)
        assertTrue(requireNotNull(edited.caption).words.last().endMs <= 1_500L)
    }

    @Test
    fun updateText_onACaption_respreadsItsWords() {
        val edited = reduce(EditAction.UpdateText(line.copy(text = "hello there"))).text("a")
        assertEquals(listOf("hello", "there"), edited.caption?.words?.map { it.text })
    }

    @Test
    fun setCaptionTimes_wordsStayWhereSpoken_andNeighboursBound() {
        val moved = reduce(EditAction.SetCaptionTimes("a", 1_200L, 2_600L)).text("a")
        assertEquals(1_200L, moved.startMs)
        // "one" was at 1000–1300; it now starts the line, clipped to its start.
        assertEquals(listOf(CaptionWord("one", 0L, 100L), CaptionWord("two", 200L, 500L), CaptionWord("three", 700L, 1_000L)), moved.caption?.words)
        val pushed = reduce(EditAction.SetCaptionTimes("a", 1_000L, 9_000L)).text("a")
        assertEquals(3_000L, pushed.endMs) // the next caption's start
        val early = reduce(EditAction.SetCaptionTimes("b", 2_000L, 3_600L)).text("b")
        assertEquals(2_500L, early.startMs) // the previous caption's end
    }

    @Test
    fun splitCaption_atAWord() {
        val split = reduce(EditAction.SplitCaption("a", 1, "a2"))
        val first = split.text("a")
        val second = split.text("a2")
        assertEquals("one" to "two three", first.text to second.text)
        assertEquals(1_400L, first.endMs)
        assertEquals(1_400L, second.startMs)
        assertEquals(listOf(CaptionWord("two", 0L, 300L), CaptionWord("three", 500L, 800L)), second.caption?.words)
        assertEquals(listOf("t", "a", "a2", "b"), split.textOverlays.map { it.id })
        assertTrue(reduce(EditAction.SplitCaption("a", 0, "x")) === base)
    }

    @Test
    fun splitText_onACaption_cutsBetweenWords() {
        val split = reduce(EditAction.SplitText("a", 1_850L, "a2"))
        assertEquals("one two" to "three", split.text("a").text to split.text("a2").text)
        assertEquals(1_900L, split.text("a2").startMs)
    }

    @Test
    fun splitIndex_keepsAWordEachSide() {
        assertEquals(1, line.captionSplitIndex(900L))
        assertEquals(2, line.captionSplitIndex(1_500L))
        assertEquals(2, line.captionSplitIndex(5_000L))
        assertNull(next.captionSplitIndex(3_200L))
    }

    @Test
    fun mergeWithNext() {
        val merged = reduce(EditAction.MergeCaptionWithNext("a"))
        val joined = merged.text("a")
        assertEquals("one two three four", joined.text)
        assertEquals(1_000L to 3_600L, joined.startMs to joined.endMs)
        assertEquals(CaptionWord("four", 2_100L, 2_500L), joined.caption?.words?.last())
        assertEquals(listOf("t", "a"), merged.textOverlays.map { it.id })
        assertTrue(reduce(EditAction.MergeCaptionWithNext("b")) === base)
    }

    @Test
    fun heardWords_replaceTheLineAndKeepItsTimes() {
        val heard = listOf(
            TranscriptWord("before", 600L, 900L), // mostly before the line
            TranscriptWord("won", 1_000L, 1_300L),
            TranscriptWord("too", 1_400L, 1_700L),
            TranscriptWord("after", 2_400L, 2_800L), // its middle is past the end
        )
        val words = line.wordsHeardIn(heard)
        assertEquals(listOf("won", "too"), words.map { it.text })
        val edited = reduce(EditAction.SetHeardWords("a", words)).text("a")
        assertEquals("won too", edited.text)
        assertEquals(1_000L to 2_500L, edited.startMs to edited.endMs)
        assertEquals(listOf(CaptionWord("won", 0L, 300L), CaptionWord("too", 400L, 700L)), edited.caption?.words)
        assertTrue(reduce(EditAction.SetHeardWords("a", emptyList())) === base)
    }

    @Test
    fun restyle_everyCaptionNotOtherTexts() {
        val styled = reduce(EditAction.RestyleCaptions(CaptionStyle.BOX))
        assertEquals(listOf("box", "box"), styled.captionsInOrder().map { it.caption?.styleId })
        assertEquals(title, styled.text("t"))
        assertEquals(CaptionStyle.BOX, styled.captionsInOrder().sharedCaptionStyle())
        assertNull(base.captionsInOrder().sharedCaptionStyle())
    }

    @Test
    fun styleAllCaptions_othersTakeTheLookAndKeepTheirWords() {
        val green = 0xFF00FF00.toInt()
        val styled = reduce(EditAction.StyleAllCaptions(line.copy(colorArgb = green, fontId = "anton")))
        assertEquals(listOf(green, green), styled.captionsInOrder().map { it.colorArgb })
        assertEquals("four", styled.text("b").text)
        assertEquals(next.caption, styled.text("b").caption)
        assertEquals(title, styled.text("t"))
    }

    @Test
    fun captionAt_isTheOneShowing() {
        val ordered = base.captionsInOrder()
        assertEquals("a", ordered.captionAt(1_000L)?.id)
        assertNull(ordered.captionAt(2_700L))
        assertEquals("b", ordered.captionAt(3_000L)?.id)
    }

    @Test
    fun typingAndNudgingCoalesce() {
        assertEquals(coalesceKeyFor(EditAction.UpdateText(line)), coalesceKeyFor(EditAction.SetCaptionText("a", "x")))
        assertEquals(coalesceKeyFor(EditAction.SetCaptionTimes("a", 0, 1)), coalesceKeyFor(EditAction.SetCaptionTimes("a", 5, 9)))
        assertEquals(coalesceKeyFor(EditAction.StyleAllCaptions(line)), coalesceKeyFor(EditAction.StyleAllCaptions(next)))
    }
}
