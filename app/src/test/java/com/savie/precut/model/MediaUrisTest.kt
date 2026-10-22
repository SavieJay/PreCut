package com.savie.precut.model

import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.sticker.Sticker
import com.savie.precut.model.sticker.StickerKind
import com.savie.precut.model.sticker.StickerPack
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
            TextOverlay(id = "p", text = "", startMs = 0, endMs = 1, sticker = StickerPack.THREE_D.sticker(StickerPack.THREE_D.stickers.first())),
        ),
    )

    @Test
    fun `every media reference is listed, emoji and pack stickers are not`() {
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
        // A pack sticker is the app's own picture, not the project's: it never moves.
        assertEquals(project.textOverlays[2].sticker, mapped.textOverlays[2].sticker)
        assertEquals(project.clips[0].trimEndMs, mapped.clips[0].trimEndMs)
    }
}
