package com.savie.precut.model.sync

import com.savie.precut.model.AudioClip
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 30D: a project's files as the cloud names them, and back. */
class CloudFilesTest {

    private val sha = "a".repeat(64)
    private val song = "b".repeat(64)

    @Test
    fun `cloud references read and write the same way`() {
        assertEquals("cloud:$sha.mp4", CloudRef(sha, "mp4").toString())
        assertEquals(CloudRef(sha, "mp4"), CloudRef.parse("cloud:$sha.mp4"))
        assertEquals(CloudRef(sha, null), CloudRef.parse("cloud:$sha"))
        assertNull(CloudRef.parse("cloud:abc.mp4"))
        assertNull(CloudRef.parse("content://media/1"))
    }

    @Test
    fun `extensions come from the name, short and lower case`() {
        assertEquals("mp4", extensionOf("file:///data/imports/clip.MP4"))
        assertEquals("cube", extensionOf("teal.cube"))
        assertNull(extensionOf("content://media/external/video/media/42"))
        assertNull(extensionOf("file:///data/x.averyverylongext"))
    }

    @Test
    fun `a project goes to the cloud and comes back with every file in place`() {
        val local = project(videoClip("1")).copy(
            audioClips = listOf(AudioClip("m", "file:///data/imports/song.m4a", sourceDurationMs = 1_000, trimEndMs = 1_000)),
        )
        val cloud = local.toCloud(mapOf("content://media/1" to CloudRef(sha, "mp4"), "file:///data/imports/song.m4a" to CloudRef(song, "m4a")))
        assertEquals("cloud:$sha.mp4", cloud.clips.single().uri)
        assertEquals("cloud:$song.m4a", cloud.audioClips.single().uri)
        assertEquals(emptySet<String>(), cloud.localMediaUris())
        assertEquals(setOf(CloudRef(sha, "mp4"), CloudRef(song, "m4a")), cloud.cloudRefs())
        val back = cloud.fromCloud { "file:///files/cloud/${it.sha256}.${it.extension}" }
        assertEquals("file:///files/cloud/$sha.mp4", back.clips.single().uri)
        assertEquals("file:///files/cloud/$song.m4a", back.audioClips.single().uri)
    }

    @Test
    fun `a file that couldn't be read keeps its Uri`() {
        val local = project(videoClip("gone"))
        assertEquals("content://media/gone", local.toCloud(emptyMap()).clips.single().uri)
    }

    @Test
    fun `imported fonts and custom LUTs are found by their ids`() {
        val p = project(videoClip("v").copy(look = ClipLook(filterId = "custom:teal")), videoClip("w").copy(look = ClipLook(filterId = "warm"))).copy(
            textOverlays = listOf(
                TextOverlay("t", "Hi", startMs = 0, endMs = 1_000, fontId = "file:1_Lobster.ttf"),
                TextOverlay("u", "Yo", startMs = 0, endMs = 1_000, fontId = "inter"),
            ),
        )
        assertEquals(setOf("file:1_Lobster.ttf"), p.importedFontIds())
        assertEquals(setOf("custom:teal"), p.customLutIds())
    }
}
