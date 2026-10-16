package com.savie.precut.model.sticker

import com.savie.precut.data.AppMediaJanitor
import com.savie.precut.media.compositionSpecOf
import com.savie.precut.model.MAX_TEXT_SIZE_SP
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.edit.BatchEdits
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.project
import com.savie.precut.model.stickerLanes
import com.savie.precut.model.textLanes
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerModelTest {
    private val fire = stickerOverlay("s1", Sticker(StickerKind.EMOJI, "🔥"), startMs = 2_000L)
    private val picture = stickerOverlay("s2", Sticker(StickerKind.IMAGE, "file:///imports/sticker_a.gif", animated = true), startMs = 0L)
    private val drawing = stickerOverlay("s3", Sticker(StickerKind.DRAWING, "file:///drawings/d.png", 0.5625f), startMs = 0L, fullFrameHeightSp = 640f)
    private val words = TextOverlay("t", "Hi", 0L, 3_000L)

    @Test
    fun stickerOverlay_isCentredForThreeSeconds() {
        assertEquals(2_000L to 5_000L, fire.startMs to fire.endMs)
        assertEquals(0.5f to 0.5f, fire.xNorm to fire.yNorm)
        assertEquals(DEFAULT_STICKER_SIZE_SP, fire.textSizeSp)
        assertEquals(640f, drawing.textSizeSp)
        assertTrue(fire.isSticker)
        assertFalse(words.isSticker)
    }

    @Test
    fun stickers_getTheirOwnRows() {
        val p = project(videoClip()).copy(textOverlays = listOf(words, fire, picture))
        assertEquals(listOf(listOf(words)), p.textLanes())
        // The picture (0–3 s) and the emoji (2–5 s) overlap, so two rows.
        assertEquals(listOf(listOf(picture), listOf(fire)), p.stickerLanes())
    }

    @Test
    fun sizeRange_letsStickersGrowPastText() {
        assertEquals(MAX_TEXT_SIZE_SP, words.sizeRange().endInclusive)
        assertEquals(MAX_STICKER_SIZE_SP, fire.sizeRange().endInclusive)
        assertTrue(drawing.textSizeSp in drawing.sizeRange())
    }

    @Test
    fun stickers_areRenderedThoughTheyHaveNoWords() {
        val spec = compositionSpecOf(project(videoClip()).copy(textOverlays = listOf(words, fire, TextOverlay("blank", " ", 0L, 1_000L))))
        assertEquals(listOf("t", "s1"), spec.texts.map { it.id })
        assertEquals(fire.sticker, spec.texts[1].style.sticker)
    }

    @Test
    fun applyTextStyleToAll_leavesStickersAlone() {
        val styled = words.copy(colorArgb = 0xFF00FF00.toInt(), bold = true)
        val batch = BatchEdits.textStyle(project(videoClip()).copy(textOverlays = listOf(styled, fire, TextOverlay("t2", "Yo", 0L, 1_000L))), styled)
        assertEquals(listOf("t2"), batch.actions.map { (it as EditAction.UpdateText).overlay.id })
    }

    @Test
    fun pictureStickersKeepTheirFilesFromTheSweep() {
        val p = project(videoClip("a")).copy(textOverlays = listOf(fire, picture, drawing))
        assertEquals(
            listOf("content://media/a", "file:///imports/sticker_a.gif", "file:///drawings/d.png"),
            AppMediaJanitor.mediaUris(p),
        )
    }
}
