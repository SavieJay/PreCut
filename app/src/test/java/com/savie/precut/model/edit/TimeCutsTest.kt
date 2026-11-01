package com.savie.precut.model.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.effect.EffectLayer
import com.savie.precut.model.effect.EffectSettings
import com.savie.precut.model.effect.EffectType
import com.savie.precut.model.project
import com.savie.precut.model.text.CaptionInfo
import com.savie.precut.model.text.CaptionWord
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeCutsTest {
    /** Two 10 s clips; a caption over 1–4 s with three words; music and a voiceover. */
    private val base = project(videoClip("a"), videoClip("b")).copy(
        textOverlays = listOf(
            TextOverlay(
                id = "c", text = "so um yes", startMs = 1_000L, endMs = 4_000L,
                caption = CaptionInfo("g", listOf(CaptionWord("so", 0L, 500L), CaptionWord("um", 1_000L, 1_400L), CaptionWord("yes", 2_000L, 2_500L))),
            ),
            TextOverlay(id = "title", text = "Title", startMs = 12_000L, endMs = 14_000L),
        ),
        audioClips = listOf(
            AudioClip(id = "m", uri = "m", sourceDurationMs = 60_000L, trimEndMs = 60_000L, kind = AudioKind.MUSIC, timelineStartMs = 0L),
            AudioClip(id = "vo", uri = "vo", sourceDurationMs = 8_000L, trimEndMs = 8_000L, kind = AudioKind.VOICEOVER, timelineStartMs = 0L),
        ),
        effectLayers = listOf(EffectLayer("fx", 15_000L, 16_000L, EffectSettings(EffectType.entries.first()))),
    )

    @Test
    fun `ranges merge, clamp and drop slivers, and time maps through them`() {
        val cuts = normalizedRanges(listOf(TimeRange(5_000L, 6_000L), TimeRange(5_500L, 7_000L), TimeRange(-10L, 20L), TimeRange(30_000L, 40_000L)), 20_000L)
        assertEquals(listOf(TimeRange(5_000L, 7_000L)), cuts)
        assertEquals(4_000L, mapThroughCuts(4_000L, cuts))
        assertEquals(5_000L, mapThroughCuts(6_000L, cuts))
        assertEquals(8_000L, mapThroughCuts(10_000L, cuts))
    }

    @Test
    fun `a cut inside a clip splits it and closes the gap, everything after moving with it`() {
        val cut = base.withoutTimeRanges(listOf(TimeRange(2_000L, 2_400L)))
        assertEquals(listOf("a", "a~1", "b"), cut.clips.map { it.id })
        assertEquals(19_600L, cut.totalDurationMs())
        assertEquals(2_000L, cut.clips[0].trimEndMs)
        assertEquals(2_400L, cut.clips[1].trimStartMs)
        // The caption loses "um"; its other words stay on their moments.
        val caption = cut.textOverlays.first { it.id == "c" }
        assertEquals("so yes", caption.text)
        assertEquals(listOf(0L, 1_600L), caption.caption?.words?.map { it.startMs })
        assertEquals(11_600L, cut.textOverlays.first { it.id == "title" }.startMs)
        assertEquals(14_600L, cut.effectLayers.single().startMs)
        // Music plays on; the voiceover is cut with the picture.
        assertEquals(listOf("m"), cut.audioClips.filter { it.kind == AudioKind.MUSIC }.map { it.id })
        val voice = cut.audioClips.filter { it.kind == AudioKind.VOICEOVER }
        assertEquals(listOf(0L to (0L to 2_000L), 2_000L to (2_400L to 8_000L)), voice.map { it.timelineStartMs to (it.trimStartMs to it.trimEndMs) })
    }

    @Test
    fun `whole clips and texts inside a cut go, but never the last clip`() {
        val cut = base.withoutTimeRanges(listOf(TimeRange(10_000L, 20_000L)))
        assertEquals(listOf("a"), cut.clips.map { it.id })
        assertTrue(cut.textOverlays.none { it.id == "title" })
        assertTrue(cut.effectLayers.isEmpty())
        assertSame(base, base.withoutTimeRanges(listOf(TimeRange(0L, 20_000L))))
    }

    @Test
    fun `fillers, silences and word cuts come from the captions`() {
        val words = base.spokenWords()
        assertEquals(listOf("so", "um", "yes"), words.map { it.text })
        assertEquals(setOf(1), fillerIndices(words))
        val youKnow = listOf(SpokenWord("You", 0, 100, "c"), SpokenWord("know,", 100, 200, "c"), SpokenWord("it", 300, 400, "c"))
        assertEquals(setOf(0, 1), fillerIndices(youKnow))
        val silences = silenceRanges(words, base.totalDurationMs())
        // 0–1 s before speech, 1.5–2 s is too short, 2.5–3 s too short, after 3.5 s to the end.
        assertEquals(listOf(TimeRange(0L, 850L), TimeRange(3_650L, 20_000L)), silences)
        assertEquals(listOf(TimeRange(1_850L, 2_550L)), wordCuts(words, setOf(1)))
        val all = base.transcriptCuts(words, setOf(1), silences)
        assertEquals(3, all.size)
    }
}
