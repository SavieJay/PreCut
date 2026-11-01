package com.savie.precut.model.brand

import com.savie.precut.model.ClipType
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.project
import com.savie.precut.model.text.CaptionInfo
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandKitTest {
    private val kit = BrandKit(
        logo = BrandMedia("content://logo", ClipType.IMAGE, width = 400, height = 200),
        colors = listOf(0xFFFF0000.toInt()),
        titleFontId = "anton",
        bodyFontId = "inter",
        intro = BrandMedia("content://intro", ClipType.VIDEO, durationMs = 2_000L, width = 1080, height = 1920),
        outro = BrandMedia("content://outro", ClipType.VIDEO, durationMs = 1_000L, width = 1080, height = 1920),
    )
    private val base = project(videoClip("a")).copy(
        textOverlays = listOf(
            TextOverlay(id = "t", text = "Hi", startMs = 1_000L, endMs = 3_000L),
            TextOverlay(id = "c", text = "words", startMs = 4_000L, endMs = 5_000L, caption = CaptionInfo("g")),
        ),
    )

    @Test
    fun `intro, outro, logo, colour and fonts in one step, texts staying with their moments`() {
        val branded = EditReducer.reduce(base, EditAction.ApplyBrandKit(kit, "1", 3_000L))
        assertEquals(listOf("brand-intro-1", "a", "brand-outro-1"), branded.clips.map { it.id })
        assertEquals(13_000L, branded.totalDurationMs())
        val title = branded.textOverlays.first { it.id == "t" }
        assertEquals(3_000L, title.startMs)
        assertEquals("anton", title.fontId)
        assertEquals(0xFFFF0000.toInt(), title.colorArgb)
        assertEquals("inter", branded.textOverlays.first { it.id == "c" }.fontId)
        val logo = branded.textOverlays.first { it.id == "brand-logo-1" }
        assertEquals(13_000L, logo.endMs)
        assertEquals(2f, logo.sticker?.aspect)
        assertTrue(branded.isBranded())
        assertFalse(base.isBranded())
    }

    @Test
    fun `applying again replaces the kit's items instead of adding more`() {
        val once = base.withBrandKit(kit, "1", 3_000L)
        val twice = once.withBrandKit(kit.copy(intro = null), "2", 3_000L)
        assertEquals(listOf("a", "brand-outro-2"), twice.clips.map { it.id })
        assertEquals(1_000L, twice.textOverlays.first { it.id == "t" }.startMs)
        assertEquals(1, twice.textOverlays.count { it.id.startsWith(BRAND_LOGO) })
    }
}
