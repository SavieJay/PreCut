package com.savie.precut.model.template

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.MAX_IMAGE_DURATION_MS
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.OverlayClip
import com.savie.precut.model.ProjectCover
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipById
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.project
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectTemplateTest {

    private fun photo(id: String, durationMs: Long) =
        Clip(id = id, uri = "content://media/$id", type = ClipType.IMAGE, sourceDurationMs = MAX_IMAGE_DURATION_MS, trimEndMs = durationMs)

    private fun video(durationMs: Long) = MediaInfo(ClipType.VIDEO, durationMs, 1920, 1080, 0)
    private val image = MediaInfo(ClipType.IMAGE, 0L, 1080, 1080, 0)

    /** Intro (fixed), two video slots and a photo slot, an overlay slot starting at 1.5 s, and two texts. */
    private val source = project(
        videoClip("intro", sourceDurationMs = 1_000),
        videoClip("a", sourceDurationMs = 10_000, trimStartMs = 2_000, trimEndMs = 4_000).copy(
            look = ClipLook(filterId = "basic.warm"),
            animationIn = ClipAnimation(AnimationType.ZOOM),
            transitionOut = ClipTransition(TransitionType.FADE_BLACK),
        ),
        videoClip("b", sourceDurationMs = 10_000, trimEndMs = 1_500),
        photo("c", 2_000),
    ).copy(
        overlays = listOf(OverlayClip(videoClip("o", sourceDurationMs = 5_000, trimEndMs = 1_000), timelineStartMs = 1_500)),
        textOverlays = listOf(TextOverlay(id = "title", text = "Lagos", startMs = 0, endMs = 1_000), TextOverlay(id = "credit", text = "by me", startMs = 5_000, endMs = 6_000)),
        cover = ProjectCover.Image("content://cover"),
    )

    private val template = source.toTemplate("t1", "Weekend", TemplateCategory.TRAVEL, setOf("a", "b", "c", "o"), setOf("title"), nowMs = 9)

    @Test
    fun `slots become placeholders in timeline order, the rest stays`() {
        // a starts at 1 s, o at 1.5 s, b at 3 s, c at 4.5 s.
        assertEquals(listOf("a", "o", "b", "c"), template.slots.map { it.clipId })
        assertEquals(SLOT_URI_PREFIX + "0", template.project.clipById("a")?.uri)
        assertEquals(SLOT_URI_PREFIX + "1", template.project.clipById("o")?.uri)
        assertEquals("content://media/intro", template.project.clipById("intro")?.uri)
        assertEquals(listOf("title"), template.texts.map { it.textId })
        assertNull(template.project.cover)
        assertEquals("t1", template.project.id)
        assertEquals(TemplateCategory.TRAVEL, template.category)
    }

    @Test
    fun `filling keeps each slot's settings and length`() {
        val filled = template.fill(listOf(TemplatePick("content://p1", video(20_000)), TemplatePick("content://p2", video(20_000))), "new", nowMs = 50)
        val a = requireNotNull(filled.project.clipById("a"))
        assertEquals("content://p1", a.uri)
        assertEquals(2_000L, a.timelineDurationMs())
        assertEquals("basic.warm", a.look.filterId)
        assertEquals(AnimationType.ZOOM, a.animationIn?.type)
        assertEquals(TransitionType.FADE_BLACK, a.transitionOut?.type)
        assertEquals(TemplateRef("t1", "Weekend"), filled.project.template)
        assertEquals("new", filled.project.id)
        assertEquals("", filled.project.name)
        assertEquals(50L, filled.project.createdAt)
    }

    @Test
    fun `fewer picks repeat, extra picks are counted`() {
        val one = template.fill(listOf(TemplatePick("content://p1", video(20_000))), "n", 0)
        assertEquals(listOf("content://p1"), template.slots.map { one.project.clipById(it.clipId)?.uri }.distinct())
        val many = template.fill((1..6).map { TemplatePick("content://p$it", video(20_000)) }, "n", 0)
        assertEquals(2, many.ignoredPicks)
        assertEquals("content://p2", many.project.clipById("o")?.uri)
    }

    @Test
    fun `photos and videos swap into each other's slots at the slot's length`() {
        val filled = template.fill(listOf(TemplatePick("content://img", image), TemplatePick("content://v", video(20_000)), TemplatePick("content://img2", image), TemplatePick("content://v2", video(20_000))), "n", 0)
        assertEquals(2_000L, filled.project.clipById("a")?.timelineDurationMs())
        assertEquals(ClipType.IMAGE, filled.project.clipById("a")?.type)
        assertEquals(2_000L, filled.project.clipById("c")?.timelineDurationMs())
        assertEquals(ClipType.VIDEO, filled.project.clipById("c")?.type)
    }

    @Test
    fun `a short video is slowed to fill its slot, a much shorter one stays short`() {
        val slowed = template.fill(listOf(TemplatePick("content://v", video(1_500))), "n", 0)
        val a = requireNotNull(slowed.project.clipById("a"))
        assertEquals(0.75f, a.speed, 0.001f)
        assertEquals(2_000L, a.timelineDurationMs())
        assertEquals(1, slowed.slowed.coerceAtMost(1))

        val short = template.fill(listOf(TemplatePick("content://v", video(500))), "n", 0)
        assertEquals(500L, short.project.clipById("a")?.timelineDurationMs())
        assertEquals(1f, short.project.clipById("a")?.speed)
        assertEquals(true, short.shorter > 0)
    }

    @Test
    fun `an overlay slot keeps its place on the timeline`() {
        val filled = template.fill(listOf(TemplatePick("content://v", video(20_000))), "n", 0)
        assertEquals(1_500L, filled.project.overlays.single().timelineStartMs)
        assertEquals("content://v", filled.project.overlays.single().clip.uri)
    }
}
