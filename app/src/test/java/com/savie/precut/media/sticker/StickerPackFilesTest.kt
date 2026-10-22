package com.savie.precut.media.sticker

import com.savie.precut.model.sticker.StickerKind
import com.savie.precut.model.sticker.StickerPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled sticker packs (Phase 19): every sticker in the catalogue has its file in the app's
 * assets and every file is in the catalogue; the animated pack's files play through the app's own
 * WebP reader ([WebpAnimation]) and the 3D pack's are stills.
 */
class StickerPackFilesTest {

    private fun dir(pack: StickerPack) = File("src/main/assets/${StickerPack.ASSET_DIR}/${pack.id}")

    @Test
    fun catalogueAndFilesMatch() {
        StickerPack.entries.forEach { pack ->
            val names = pack.stickers.map { it.name }
            assertEquals(pack.id, names.size, names.toSet().size)
            val files = dir(pack).listFiles().orEmpty().map { it.name }.toSet()
            assertEquals(pack.id, names.map { "$it.webp" }.toSet(), files)
            pack.stickers.forEach { item -> assertTrue(File("src/main/assets/${pack.assetPath(item)}").isFile) }
        }
    }

    @Test
    fun animatedStickers_playThroughTheAppsReader() {
        StickerPack.ANIMATED.stickers.forEach { item ->
            val bytes = File("src/main/assets/${StickerPack.ANIMATED.assetPath(item)}").readBytes()
            val durations = mutableListOf<Int>()
            // Each frame must be a standalone WebP of the size its ANMF chunk says, or decode gives null.
            val info = WebpAnimation.decode(bytes, decodeFrame = { frame -> standaloneSize(frame).let { (w, h) -> IntArray(w * h) } }) { _, ms -> durations += ms }
            assertNotNull(item.name, info)
            assertEquals(item.name, 192 to 192, info?.let { it.width to it.height })
            assertTrue(item.name, durations.size >= 2)
            assertTrue(item.name, durations.sum() in 500..5_000)
        }
    }

    @Test
    fun threeDStickers_areStills() {
        StickerPack.THREE_D.stickers.forEach { item ->
            val bytes = File("src/main/assets/${StickerPack.THREE_D.assetPath(item)}").readBytes()
            assertFalse(item.name, WebpAnimation.isAnimated(bytes))
            assertEquals(item.name, 256 to 256, standaloneSize(bytes))
        }
    }

    @Test
    fun aPlacedPackSticker_findsItsItemAgain() {
        StickerPack.entries.forEach { pack ->
            pack.stickers.forEach { item ->
                val sticker = pack.sticker(item)
                assertEquals(StickerKind.PACK, sticker.kind)
                assertEquals(pack.animated, sticker.animated)
                assertEquals(pack to item, StickerPack.of(sticker))
            }
        }
        // A pack or name this version doesn't ship (a newer app's draft) isn't found, so it draws as an empty box.
        assertEquals(null, StickerPack.of(StickerPack.THREE_D.sticker(StickerPack.THREE_D.stickers[0]).copy(value = "fluent_3d/unknown")))
        assertEquals(null, StickerPack.of(StickerPack.THREE_D.sticker(StickerPack.THREE_D.stickers[0]).copy(value = "other/fire")))
    }
}
