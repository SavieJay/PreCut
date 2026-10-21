package com.savie.precut.model

import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.sticker.Sticker
import com.savie.precut.model.sticker.StickerKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaUrisTest {

    private val project = project(
        videoClip("a").copy(
            canvas = ClipCanvas(kind = CanvasKind.IMAGE, imageUri = "content://canvas"),
            reversedFrom = ReverseSource("content://original", 1, 0, 1, 1, 1, 0),
        ),
    ).copy(
        overlays = listOf(OverlayClip(videoClip("o"), 0)),
        audioClips = listOf(AudioClip(id = "m", uri = "content://song", sourceDurationMs = 1_000, trimEndMs = 1_000)),
        cover = ProjectCover.Image("content://cover"),
        textOverlays = listOf(
            TextOverlay(id = "s", text = "", startMs = 0, endMs = 1, sticker = Sticker(StickerKind.IMAGE, "file:///sticker.png")),
            TextOverlay(id = "e", text = "", startMs = 0, endMs = 1, sticker = Sticker(StickerKind.EMOJI, "🎉")),
        ),
    )

    @Test
    fun `every media reference is listed, emoji are not`() {
        assertEquals(
            setOf("content://media/a", "content://media/o", "content://song", "content://cover", "content://canvas", "content://original", "file:///sticker.png"),
            project.mediaUris().toSet(),
        )
    }

    @Test
    fun `mapping changes every listed reference and nothing else`() {
        val mapped = project.mapMediaUris { "x:$it" }
        assertEquals(project.mediaUris().map { "x:$it" }.toSet(), mapped.mediaUris().toSet())
        assertEquals("🎉", mapped.textOverlays[1].sticker?.value)
        assertEquals(project.clips[0].trimEndMs, mapped.clips[0].trimEndMs)
    }
}
