package com.savie.precut.data

import com.savie.precut.model.AudioClip
import com.savie.precut.model.ProjectCover
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
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
    fun unusedFiles_acrossAllAppMediaFolders() {
        val dirs = APP_MEDIA_DIRS.map { temp.newFolder(it) }
        val kept = File(dirs[1], "used.mp4").apply { createNewFile() }
        val stale = dirs.map { File(it, "stale.bin").apply { createNewFile() } }
        val unused = AppMediaJanitor.unusedFiles(dirs, setOf("uri:${kept.name}")) { "uri:${it.name}" }
        assertEquals(stale.map { it.path }.toSet(), unused.map { it.path }.toSet())
    }
}
