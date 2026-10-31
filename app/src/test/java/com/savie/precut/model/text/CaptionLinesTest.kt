package com.savie.precut.model.text

import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.ai.TranscriptWord
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionLinesTest {

    /** Words spoken back to back, 300 ms each. */
    private fun spoken(vararg texts: String, startMs: Long = 0L): List<TranscriptWord> =
        texts.mapIndexed { i, text -> TranscriptWord(text, startMs + i * 300L, startMs + i * 300L + 250L) }

    private fun lineTexts(lines: List<List<TranscriptWord>>) = lines.map { line -> line.joinToString(" ") { it.text } }

    @Test
    fun sentenceEnd_closesTheLine() {
        val lines = CaptionLines.build(spoken("Hello", "there.", "How", "are", "you?"))
        assertEquals(listOf("Hello there.", "How are you?"), lineTexts(lines))
    }

    @Test
    fun pause_startsANewLine() {
        val words = spoken("one", "two") + spoken("three", startMs = 2_000L)
        assertEquals(listOf("one two", "three"), lineTexts(CaptionLines.build(words)))
    }

    @Test
    fun lineNeverGrowsPastMaxChars() {
        val words = spoken("aaaaaaaaaa", "bbbbbbbbbb", "cccccccccc", "dddddddddd")
        val lines = lineTexts(CaptionLines.build(words, maxLineMs = 60_000L))
        assertEquals(listOf("aaaaaaaaaa bbbbbbbbbb cccccccccc", "dddddddddd"), lines)
        assertTrue(lines.all { it.length <= CaptionLines.MAX_LINE_CHARS })
    }

    @Test
    fun lineNeverLastsPastMaxDuration() {
        // Twelve short words at 300 ms each: 3.55 s in all.
        val words = spoken("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l")
        val lines = CaptionLines.build(words)
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.last().endMs - it.first().startMs <= CaptionLines.MAX_LINE_MS })
    }

    @Test
    fun comma_closesOnlyAHalfFullLine() {
        assertEquals(listOf("So, we went home"), lineTexts(CaptionLines.build(spoken("So,", "we", "went", "home"))))
        val long = spoken("After", "a", "very", "long", "day,", "we", "rested")
        assertEquals(listOf("After a very long day,", "we rested"), lineTexts(CaptionLines.build(long)))
    }

    @Test
    fun blankWordsAreDroppedAndOrderFollowsTime() {
        val words = listOf(TranscriptWord("world", 400L, 600L), TranscriptWord("  ", 300L, 350L), TranscriptWord(" Hello ", 0L, 250L))
        assertEquals(listOf("Hello world"), lineTexts(CaptionLines.build(words)))
    }

    @Test
    fun aWordLongerThanALineStandsAlone() {
        val words = spoken("Hi", "Pneumonoultramicroscopicsilicovolcanoconiosis", "there")
        assertEquals(listOf("Hi", "Pneumonoultramicroscopicsilicovolcanoconiosis", "there"), lineTexts(CaptionLines.build(words)))
    }

    @Test
    fun texts_areStyledCaptionsWithWordTimesIntoEach() {
        val lines = CaptionLines.build(spoken("Hello", "there.", startMs = 1_000L))
        var next = 0
        val texts = CaptionLines.texts(lines, groupId = "g", style = CaptionStyle.HIGHLIGHT, language = "en") { "c${next++}" }
        val caption = texts.single()
        assertEquals("c0", caption.id)
        assertEquals("Hello there.", caption.text)
        assertEquals(1_000L, caption.startMs)
        // The last word ends at 1550; the line stays up a little after it.
        assertEquals(1_550L + CaptionLines.LINGER_MS, caption.endMs)
        val info = requireNotNull(caption.caption)
        assertEquals("g", info.groupId)
        assertEquals("en", info.language)
        assertEquals(listOf(CaptionWord("Hello", 0L, 250L), CaptionWord("there.", 300L, 550L)), info.words)
        assertEquals(CaptionStyle.HIGHLIGHT.id, info.styleId)
        assertEquals(CaptionStyle.HIGHLIGHT.highlightArgb, info.highlightArgb)
        assertTrue(caption.bold)
    }

    @Test
    fun texts_lingerStopsWhereTheNextLineStarts() {
        val lines = CaptionLines.build(spoken("One.") + spoken("Two.", startMs = 400L))
        val texts = CaptionLines.texts(lines, "g", CaptionStyle.CLASSIC, null) { "id" }
        assertEquals(400L, texts[0].endMs)
        assertEquals(400L, texts[1].startMs)
    }

    @Test
    fun setCaptions_replacesEveryCaptionAndKeepsOtherTexts() {
        val own = TextOverlay("own", "Title", 0L, 1_000L)
        val old = TextOverlay("old", "Old caption", 0L, 1_000L, caption = CaptionInfo("old-group"))
        val base = project(videoClip()).copy(textOverlays = listOf(own, old))
        val fresh = CaptionLines.texts(CaptionLines.build(spoken("New", "words.")), "new-group", CaptionStyle.BOX, null) { "new" }
        val edited = EditReducer.reduce(base, EditAction.SetCaptions(fresh))
        assertEquals(listOf("own", "new"), edited.textOverlays.map { it.id })
        assertEquals(1, edited.captionCount())
    }

    @Test
    fun setCaptions_withNothingAndNoCaptions_changesNothing() {
        val base = project(videoClip()).copy(textOverlays = listOf(TextOverlay("own", "Title", 0L, 1_000L)))
        assertTrue(EditReducer.reduce(base, EditAction.SetCaptions(emptyList())) === base)
    }

    @Test
    fun withoutMusic_keepsVoiceoversAndExtractedSound() {
        fun audio(id: String, kind: AudioKind) = AudioClip(id, "content://$id", 5_000L, trimEndMs = 5_000L, kind = kind)
        val base = project(videoClip()).copy(audioClips = listOf(audio("m", AudioKind.MUSIC), audio("v", AudioKind.VOICEOVER), audio("x", AudioKind.EXTRACTED)))
        assertEquals(listOf("v", "x"), base.withoutMusic().audioClips.map { it.id })
    }

    @Test
    fun hasSoundBesidesMusic() {
        val video = project(videoClip())
        assertTrue(video.hasSoundBesidesMusic())
        assertFalse(video.copy(originalAudioMuted = true).hasSoundBesidesMusic())
        val music = AudioClip("m", "content://m", 5_000L, trimEndMs = 5_000L, kind = AudioKind.MUSIC)
        assertFalse(video.copy(originalAudioMuted = true, audioClips = listOf(music)).hasSoundBesidesMusic())
        val voice = music.copy(id = "v", kind = AudioKind.VOICEOVER)
        assertTrue(video.copy(originalAudioMuted = true, audioClips = listOf(voice)).hasSoundBesidesMusic())
    }

    @Test
    fun noWords_noLines() {
        assertTrue(CaptionLines.build(emptyList()).isEmpty())
        assertNull(CaptionLines.texts(listOf(emptyList()), "g", CaptionStyle.CLASSIC, null) { "id" }.firstOrNull())
    }
}
