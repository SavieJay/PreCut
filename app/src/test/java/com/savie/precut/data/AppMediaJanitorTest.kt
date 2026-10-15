package com.savie.precut.data

import com.savie.precut.model.AudioClip
import com.savie.precut.model.OverlayClip
import com.savie.precut.model.ProjectCover
import com.savie.precut.model.ReverseSource
import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppMediaJanitorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun mediaUris_includeClipsAudioAndCoverPhoto() {
        val p = project(videoClip("a")).copy(
            audioClips = listOf(AudioClip("m", "file:///voiceovers/m.m4a", 1_000L, trimEndMs = 1_000L)),
            cover = ProjectCover.Image("file:///imports/cover.jpg"),
        )
        assertEquals(
            listOf("content://media/a", "file:///voiceovers/m.m4a", "file:///imports/cover.jpg"),
            AppMediaJanitor.mediaUris(p),
        )
    }

    @Test
    fun mediaUris_includeCanvasPhotos() {
        val p = project(videoClip("a").copy(canvas = ClipCanvas(kind = CanvasKind.IMAGE, imageUri = "file:///imports/bg.jpg")))
        assertEquals(listOf("content://media/a", "file:///imports/bg.jpg"), AppMediaJanitor.mediaUris(p))
    }

    @Test
    fun mediaUris_includeAReversedClipsOriginal() {
        val clip = videoClip("a").copy(uri = "file:///reversed/r.mp4", reversedFrom = ReverseSource("file:///captures/c.mp4", 1L, 0L, 1L, 0, 0, 0))
        assertEquals(listOf("file:///reversed/r.mp4", "file:///captures/c.mp4"), AppMediaJanitor.mediaUris(project(clip)))
    }

    @Test
    fun mediaUris_includeOverlays() {
        val p = project(videoClip("a")).copy(overlays = listOf(OverlayClip(videoClip("o").copy(uri = "file:///captures/o.mp4"), 0L)))
        assertTrue("file:///captures/o.mp4" in AppMediaJanitor.mediaUris(p))
    }

    @Test
    fun unusedFiles_acrossAllAppMediaFolders() {
        val dirs = APP_MEDIA_DIRS.map { temp.newFolder(it) }
        val kept = File(dirs[1], "used.mp4").apply { createNewFile() }
        val stale = dirs.map { File(it, "stale.bin").apply { createNewFile() } }
        val unused = AppMediaJanitor.unusedFiles(dirs, setOf("uri:${kept.name}")) { "uri:${it.name}" }
        assertEquals(stale.map { it.path }.toSet(), unused.map { it.path }.toSet())
    }
}
