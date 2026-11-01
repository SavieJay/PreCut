package com.savie.precut.model.template

import com.savie.precut.model.AudioClip
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.project
import com.savie.precut.model.sticker.Sticker
import com.savie.precut.model.sticker.StickerKind
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateLinksTest {
    private val slot = videoClip("a").copy(uri = SLOT_URI_PREFIX + 0)
    private val template = ProjectTemplate(
        id = "t", title = "Trip", slots = listOf(TemplateSlot("a")),
        project = project(slot).copy(audioClips = listOf(AudioClip(id = "m", uri = "content://my-song", sourceDurationMs = 5_000L, trimEndMs = 5_000L))),
    )

    @Test
    fun `slots-only templates share without their music, others don't share`() {
        assertTrue(template.ownMedia().isEmpty())
        assertTrue(template.forSharing()?.project?.audioClips?.isEmpty() == true)
        val withSticker = template.copy(project = template.project.copy(textOverlays = listOf(TextOverlay("s", "", 0L, 1_000L, sticker = Sticker(StickerKind.IMAGE, "file:///data/me.png")))))
        assertEquals(listOf("file:///data/me.png"), withSticker.ownMedia())
        assertNull(withSticker.forSharing())
        val ownClip = template.copy(project = template.project.copy(clips = template.project.clips + videoClip("b")))
        assertNull(ownClip.forSharing())
    }

    @Test
    fun `links are made and read for the website's host only`() {
        assertEquals("https://precut.app/t/Abcdefgh", TemplateLinks.link("precut.app", "Abcdefgh"))
        assertEquals("Abcdefgh", TemplateLinks.idOf("https://precut.app/t/Abcdefgh", "precut.app"))
        assertEquals("Abcdefgh", TemplateLinks.idOf("https://precut.app/t/Abcdefgh/?ref=share", "precut.app"))
        assertNull(TemplateLinks.idOf("https://evil.example/t/Abcdefgh", "precut.app"))
        assertNull(TemplateLinks.idOf("https://precut.app/t/short", "precut.app"))
        assertNull(TemplateLinks.idOf("https://precut.app.evil.example/t/Abcdefgh", "precut.app"))
        assertEquals("shared-Abcdefgh", TemplateLinks.localId("Abcdefgh"))
    }
}
