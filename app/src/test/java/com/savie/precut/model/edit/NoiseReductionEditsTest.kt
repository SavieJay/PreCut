package com.savie.precut.model.edit

import com.savie.precut.data.ProjectSerializer
import com.savie.precut.media.ItemSpec
import com.savie.precut.media.compositionSpecOf
import com.savie.precut.model.AudioClip
import com.savie.precut.model.audio.NoiseReduction
import com.savie.precut.model.project
import com.savie.precut.model.template.TemplateCategory
import com.savie.precut.model.template.toTemplate
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoiseReductionEditsTest {

    private val reduction = NoiseReduction(List(32) { 1e-4f }, strength = 0.7f)
    private val song = AudioClip("m", "content://media/m", sourceDurationMs = 5_000L, trimEndMs = 5_000L)
    private val base = project(videoClip("a")).copy(audioClips = listOf(song))

    @Test
    fun clipAndAudio_setClampAndClear() {
        val on = EditReducer.reduce(base, EditAction.SetClipNoiseReduction("a", reduction.copy(strength = 4f)))
        assertEquals(reduction.copy(strength = 1f), on.clips[0].noiseReduction)
        assertNull(EditReducer.reduce(on, EditAction.SetClipNoiseReduction("a", null)).clips[0].noiseReduction)
        val audio = EditReducer.reduce(base, EditAction.SetAudioNoiseReduction("m", reduction.copy(strength = -1f)))
        assertEquals(0f, audio.audioClips[0].noiseReduction?.strength)
    }

    @Test
    fun extractedAudio_keepsIt_replacedAudio_dropsIt() {
        val on = EditReducer.reduce(base, EditAction.SetClipNoiseReduction("a", reduction))
        val extracted = EditReducer.reduce(on, EditAction.ExtractAudio("a", "x"))
        assertEquals(reduction, extracted.audioClips.first { it.id == "x" }.noiseReduction)
        val withSong = EditReducer.reduce(base, EditAction.SetAudioNoiseReduction("m", reduction))
        val replaced = EditReducer.reduce(withSong, EditAction.ReplaceAudio("m", "content://media/other", 3_000L, "Other"))
        assertNull(replaced.audioClips[0].noiseReduction)
    }

    @Test
    fun splitKeeps_templateSlotsDrop() {
        val on = EditReducer.reduce(base, EditAction.SetClipNoiseReduction("a", reduction))
        val split = EditReducer.reduce(on, EditAction.SplitClip(atTimelineMs = 4_000L, newClipId = "b"))
        assertEquals(listOf(reduction, reduction), split.clips.map { it.noiseReduction })
        val template = on.toTemplate("t", "T", TemplateCategory.OTHER, slotIds = setOf("a"), textIds = emptySet(), nowMs = 0L)
        assertNull(template.project.clips[0].noiseReduction)
    }

    @Test
    fun spec_putsItInTheSound_unlessMuted() {
        val on = EditReducer.reduce(
            EditReducer.reduce(base, EditAction.SetClipNoiseReduction("a", reduction)),
            EditAction.SetAudioNoiseReduction("m", reduction),
        )
        val spec = compositionSpecOf(on)
        assertEquals(reduction, (spec.items[0] as ItemSpec.Video).sound.noiseReduction)
        assertEquals(reduction, spec.audioLanes.single().single().sound.noiseReduction)
        val muted = compositionSpecOf(EditReducer.reduce(on, EditAction.SetClipMuted("a", true)))
        assertNull((muted.items[0] as ItemSpec.Video).sound.noiseReduction)
    }

    @Test
    fun oldDrafts_haveNone_andItRoundTrips() {
        val json = """{"id":"p","name":"Old","createdAt":1,"updatedAt":2,""" +
            """"clips":[{"id":"a","uri":"content://a","type":"VIDEO","sourceDurationMs":5000,"trimEndMs":5000}],""" +
            """"audioClips":[{"id":"m","uri":"content://m","sourceDurationMs":5000,"trimEndMs":5000}]}"""
        val old = ProjectSerializer.decode(json)
        assertNull(old?.clips?.single()?.noiseReduction)
        assertNull(old?.audioClips?.single()?.noiseReduction)
        val on = EditReducer.reduce(EditReducer.reduce(base, EditAction.SetClipNoiseReduction("a", reduction)), EditAction.SetAudioNoiseReduction("m", reduction))
        assertEquals(on, ProjectSerializer.decode(ProjectSerializer.encode(on)))
    }
}
