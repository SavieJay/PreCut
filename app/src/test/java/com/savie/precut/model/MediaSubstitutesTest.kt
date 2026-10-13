package com.savie.precut.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MediaSubstitutesTest {

    private val photo = clipFromMedia("p", "content://media/a", MediaInfo(ClipType.IMAGE, 0L, 10, 10, 0))
    private val song = AudioClip("m", "content://media/a", sourceDurationMs = 5_000L, trimEndMs = 5_000L)
    private val base = project(videoClip("a"), videoClip("b"), photo).copy(audioClips = listOf(song))

    @Test
    fun swapsVideoClipsOnly_keepingTiming() {
        val swapped = base.withMediaSubstitutes(mapOf("content://media/a" to "file:///proxies/a.mp4"))
        assertEquals(listOf("file:///proxies/a.mp4", "content://media/b", "content://media/a"), swapped.clips.map { it.uri })
        assertEquals(base.clips.map { it.trimStartMs to it.trimEndMs }, swapped.clips.map { it.trimStartMs to it.trimEndMs })
        assertEquals("content://media/a", swapped.audioClips.single().uri)
    }

    @Test
    fun noRelevantSubstitute_isSameInstance() {
        assertSame(base, base.withMediaSubstitutes(emptyMap()))
        assertSame(base, base.withMediaSubstitutes(mapOf("content://other" to "file:///x.mp4")))
    }
}
