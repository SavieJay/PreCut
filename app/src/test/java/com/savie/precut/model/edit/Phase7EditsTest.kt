package com.savie.precut.model.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.ClipType
import com.savie.precut.model.FilterType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.ProjectCover
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.project
import com.savie.precut.model.textLanes
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7EditsTest {

    private val photo = clipFromMedia("p", "content://p", MediaInfo(ClipType.IMAGE, 0L, 10, 10, 0))
    private val song = AudioClip("m", "content://m", sourceDurationMs = 30_000L, trimEndMs = 10_000L)
    private val text = TextOverlay("t", "Hi", startMs = 1_000L, endMs = 4_000L)
    // Two 10 s videos and a 3 s photo: 23 s.
    private val base = project(videoClip("a"), videoClip("b"), photo).copy(audioClips = listOf(song), textOverlays = listOf(text))

    private fun reduce(action: EditAction) = EditReducer.reduce(base, action)

    @Test
    fun batch_appliesInOrder_asOneHistoryStep() {
        val batch = EditAction.Batch(listOf(EditAction.SetSpeed("a", 2f), EditAction.SetFilter("b", FilterType.WARM)))
        val edited = reduce(batch)
        assertEquals(2f, edited.clips[0].speed)
        assertEquals(FilterType.WARM, edited.clips[1].filter)
        val history = History(base).push(edited)
        assertEquals(base, history.undo()?.present)
        assertSame(base, reduce(EditAction.Batch(emptyList())))
    }

    @Test
    fun batchEdits_skipWhatDoesNotApply() {
        val ids = setOf("a", "p", "m", "t")
        // Speed: videos only (photo, audio and text skipped).
        assertEquals(listOf(EditAction.SetSpeed("a", 3f)), BatchEdits.speed(base, ids, 3f).actions)
        assertEquals(listOf("a", "p"), BatchEdits.filter(base, ids, FilterType.COOL).actions.map { (it as EditAction.SetFilter).clipId })
        val volume = reduce(BatchEdits.volume(base, ids, 0.5f))
        assertEquals(listOf(0.5f, 1f, 0.5f), volume.clips.map { it.volume })
        assertEquals(0.5f, volume.audioClips.single().volume)
    }

    @Test
    fun batchDelete_removesClipsAudioAndTexts_inOneStep() {
        val edited = reduce(BatchEdits.delete(base, setOf("a", "m", "t")))
        assertEquals(listOf("b", "p"), edited.clips.map { it.id })
        assertTrue(edited.audioClips.isEmpty())
        assertTrue(edited.textOverlays.isEmpty())
    }

    @Test
    fun batchDuplicate_copiesEachKind() {
        var next = 0
        val edited = reduce(BatchEdits.duplicate(base, setOf("b", "t", "m")) { "n${next++}" })
        assertEquals(listOf("a", "b", "n0", "p"), edited.clips.map { it.id })
        assertEquals(listOf("t", "n1"), edited.textOverlays.map { it.id })
        assertEquals(setOf("m", "n2"), edited.audioClips.map { it.id }.toSet())
    }

    @Test
    fun splitText_atTime_keepsStyle() {
        val edited = reduce(EditAction.SplitText("t", 2_500L, "t2"))
        assertEquals(listOf(1_000L to 2_500L, 2_500L to 4_000L), edited.textOverlays.map { it.startMs to it.endMs })
        assertEquals(listOf("Hi", "Hi"), edited.textOverlays.map { it.text })
        // Too close to an edge: nothing happens.
        assertSame(base, reduce(EditAction.SplitText("t", 1_050L, "t2")))
    }

    @Test
    fun duplicateText_startsWhereOriginalEnds_andStacksIfItOverlaps() {
        val edited = reduce(EditAction.DuplicateText("t", "t2"))
        assertEquals(4_000L to 7_000L, edited.textOverlays[1].let { it.startMs to it.endMs })
        // At the very end of the timeline the copy is clamped and so overlaps: it gets its own row.
        val atEnd = base.copy(textOverlays = listOf(text.copy(startMs = 21_000L, endMs = 23_000L)))
        val stacked = EditReducer.reduce(atEnd, EditAction.DuplicateText("t", "t2"))
        assertEquals(2, stacked.textLanes().size)
    }

    @Test
    fun textLanes_stackOverlaps() {
        val texts = listOf(text, text.copy(id = "u", startMs = 2_000L), text.copy(id = "v", startMs = 4_000L, endMs = 5_000L))
        assertEquals(listOf(listOf("t", "v"), listOf("u")), base.copy(textOverlays = texts).textLanes().map { lane -> lane.map { it.id } })
    }

    @Test
    fun keepPitch_isSetAndSurvivesReplace() {
        val edited = reduce(EditAction.SetKeepPitch("a", false))
        assertFalse(edited.clips[0].keepPitch)
        val replaced = EditReducer.reduce(edited, EditAction.ReplaceClip("a", "content://z", MediaInfo(ClipType.VIDEO, 20_000L, 1, 1, 0)))
        assertFalse(replaced.clips[0].keepPitch)
    }

    @Test
    fun setCover_andClearingItDropsInExport() {
        val withCover = reduce(EditAction.SetCover(ProjectCover.Frame(5_000L), inExport = true))
        assertEquals(ProjectCover.Frame(5_000L), withCover.cover)
        assertTrue(withCover.coverInExport)
        val cleared = EditReducer.reduce(withCover, EditAction.SetCover(null, inExport = true))
        assertEquals(null, cleared.cover)
        assertFalse(cleared.coverInExport)
    }
}
