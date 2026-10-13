package com.savie.precut.model.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.MAX_VOLUME
import com.savie.precut.model.VoiceEffect
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEditsTest {

    // 15 s of video: clip a (10 s) + clip b (5 s).
    private val base = project(videoClip("a", 10_000L), videoClip("b", 5_000L))
    private fun song(id: String = "m", start: Long = 0L) =
        AudioClip(id, "content://$id", sourceDurationMs = 30_000L, timelineStartMs = start, trimEndMs = 30_000L)
    private val withSong = EditReducer.reduce(base, EditAction.AddAudio(song(start = 1_000L)))
    private fun reduce(action: EditAction) = EditReducer.reduce(withSong, action)
    private fun song(project: com.savie.precut.model.Project) = project.audioClips.single()

    @Test
    fun addAudio_isClampedToTheTimeline() {
        val late = EditReducer.reduce(base, EditAction.AddAudio(song(start = 99_000L))).audioClips.single()
        assertEquals(15_000L - MIN_CLIP_DURATION_MS, late.timelineStartMs)
    }

    @Test
    fun deleteAudio_removesIt() {
        assertTrue(reduce(EditAction.DeleteAudio("m")).audioClips.isEmpty())
        assertSame(withSong, reduce(EditAction.DeleteAudio("missing")))
    }

    @Test
    fun split_cutsAtTheTimelineTime_andDividesBeatsAndFades() {
        val prepared = EditReducer.reduce(
            EditReducer.reduce(withSong, EditAction.SetAudioFades("m", 1_000L, 2_000L)),
            EditAction.ToggleBeat("m", 3_000L), // source 2 s
        ).let { EditReducer.reduce(it, EditAction.ToggleBeat("m", 9_000L)) } // source 8 s
        val split = EditReducer.reduce(prepared, EditAction.SplitAudio("m", 6_000L, "m2")).audioClips
        val (first, second) = split
        assertEquals(0L to 5_000L, first.trimStartMs to first.trimEndMs)
        assertEquals(6_000L to 5_000L, second.timelineStartMs to second.trimStartMs)
        assertEquals(1_000L to 0L, first.fadeInMs to first.fadeOutMs)
        assertEquals(0L to 2_000L, second.fadeInMs to second.fadeOutMs)
        assertEquals(listOf(2_000L), first.beatsMs)
        assertEquals(listOf(8_000L), second.beatsMs)
    }

    @Test
    fun split_tooCloseToAnEdge_isNoOp() {
        assertSame(withSong, reduce(EditAction.SplitAudio("m", 1_050L, "x")))
    }

    @Test
    fun duplicate_placesCopyRightAfter() {
        val shortSong = EditReducer.reduce(withSong, EditAction.SetAudioTiming("m", 1_000L, 0L, 4_000L))
        val copy = EditReducer.reduce(shortSong, EditAction.DuplicateAudio("m", "m2")).audioClips.last()
        assertEquals("m2" to 5_000L, copy.id to copy.timelineStartMs)
    }

    @Test
    fun replace_keepsSlotAndLength_asFarAsTheNewFileAllows() {
        val trimmed = EditReducer.reduce(withSong, EditAction.SetAudioTiming("m", 1_000L, 2_000L, 12_000L))
        val replaced = song(EditReducer.reduce(trimmed, EditAction.ReplaceAudio("m", "content://new", 6_000L, "New")))
        assertEquals("content://new", replaced.uri)
        assertEquals(1_000L, replaced.timelineStartMs)
        assertEquals(0L to 6_000L, replaced.trimStartMs to replaced.trimEndMs) // 10 s wanted, file has 6 s
    }

    @Test
    fun volumeSpeedAndVoice_areClamped() {
        assertEquals(MAX_VOLUME, song(reduce(EditAction.SetAudioVolume("m", 9f))).volume)
        assertEquals(com.savie.precut.model.MAX_AUDIO_SPEED, song(reduce(EditAction.SetAudioSpeed("m", 99f))).speed)
        assertEquals(VoiceEffect.DEEP, song(reduce(EditAction.SetVoiceEffect("m", VoiceEffect.DEEP))).voiceEffect)
    }

    @Test
    fun fades_fitTheLength_andFollowSpeedChanges() {
        val faded = reduce(EditAction.SetAudioFades("m", 20_000L, 20_000L)) // 30 s long
        assertEquals(20_000L to 10_000L, song(faded).fadeInMs to song(faded).fadeOutMs)
        val faster = EditReducer.reduce(faded, EditAction.SetAudioSpeed("m", 2f)) // now 15 s long
        assertEquals(15_000L to 0L, song(faster).fadeInMs to song(faster).fadeOutMs)
    }

    @Test
    fun toggleBeat_addsThenRemovesNearbyBeat_andIgnoresOutside() {
        val one = reduce(EditAction.ToggleBeat("m", 4_000L))
        assertEquals(listOf(3_000L), song(one).beatsMs)
        assertTrue(song(EditReducer.reduce(one, EditAction.ToggleBeat("m", 4_100L))).beatsMs.isEmpty())
        assertSame(withSong, reduce(EditAction.ToggleBeat("m", 500L))) // before the clip starts
        assertTrue(song(EditReducer.reduce(one, EditAction.ClearBeats("m"))).beatsMs.isEmpty())
    }

    @Test
    fun extract_copiesClipTiming_andMutesTheClip() {
        val fast = EditReducer.reduce(base, EditAction.SetSpeed("b", 2f))
        val extracted = EditReducer.reduce(fast, EditAction.ExtractAudio("b", "x"))
        val audio = extracted.audioClips.single()
        assertEquals(AudioKind.EXTRACTED, audio.kind)
        assertEquals(10_000L, audio.timelineStartMs) // clip b starts after clip a
        assertEquals(2f, audio.speed)
        assertEquals("content://media/b", audio.uri)
        assertTrue(extracted.clips[1].muted)
    }

    @Test
    fun clipFades_andOriginalMute() {
        val faded = EditReducer.reduce(base, EditAction.SetClipFades("b", 4_000L, 4_000L)).clips[1]
        assertEquals(4_000L to 1_000L, faded.fadeInMs to faded.fadeOutMs)
        assertTrue(EditReducer.reduce(base, EditAction.SetOriginalAudioMuted(true)).originalAudioMuted)
        assertSame(base, EditReducer.reduce(base, EditAction.SetOriginalAudioMuted(false)))
    }

    @Test
    fun clipEdits_leaveAudioUntouched() {
        assertEquals(withSong.audioClips, EditReducer.reduce(withSong, EditAction.DeleteClip("b")).audioClips)
    }
}
