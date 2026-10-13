package com.savie.precut.model.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.ClipType
import com.savie.precut.model.FilterType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReplaceAndRemoveTest {

    private val styled = videoClip("a", 10_000L, trimStartMs = 2_000L, trimEndMs = 8_000L, speed = 2f)
        .copy(filter = FilterType.GRAYSCALE, rotationDegrees = 90, volume = 0.5f)
    private val base = project(styled, videoClip("b"))

    @Test
    fun replaceWithVideo_keepsLengthAndSettings() {
        val clip = EditReducer.reduce(base, EditAction.ReplaceClip("a", "content://new", MediaInfo(ClipType.VIDEO, 20_000L, 1, 1, 0))).clips[0]
        assertEquals("content://new", clip.uri)
        assertEquals(0L to 6_000L, clip.trimStartMs to clip.trimEndMs) // same 6 s of source as before
        assertEquals(2f, clip.speed)
        assertEquals(90, clip.rotationDegrees)
        assertEquals(0.5f, clip.volume)
    }

    @Test
    fun replaceWithShorterVideo_usesAllOfIt() {
        val clip = EditReducer.reduce(base, EditAction.ReplaceClip("a", "content://short", MediaInfo(ClipType.VIDEO, 3_000L, 1, 1, 0))).clips[0]
        assertEquals(0L to 3_000L, clip.trimStartMs to clip.trimEndMs)
    }

    @Test
    fun replaceWithPhoto_keepsDuration_andDropsSpeed() {
        val clip = EditReducer.reduce(base, EditAction.ReplaceClip("a", "content://p", MediaInfo(ClipType.IMAGE, 0L, 1, 1, 0))).clips[0]
        assertEquals(ClipType.IMAGE, clip.type)
        assertEquals(6_000L, clip.trimEndMs - clip.trimStartMs)
        assertEquals(1f, clip.speed)
    }

    @Test
    fun removeItems_dropsClipsAndAudioTogether() {
        val withAudio = base.copy(audioClips = listOf(AudioClip("m", "content://m", 5_000L, trimEndMs = 5_000L)))
        val result = EditReducer.reduce(withAudio, EditAction.RemoveItems(setOf("a"), setOf("m")))
        assertEquals(listOf("b"), result.clips.map { it.id })
        assertEquals(emptyList<AudioClip>(), result.audioClips)
        assertSame(withAudio, EditReducer.reduce(withAudio, EditAction.RemoveItems(setOf("x"), emptySet())))
    }
}
