package com.savie.precut.model.template

import com.savie.precut.data.TemplateSerializer
import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.clipById
import com.savie.precut.model.look.FilterCatalog
import com.savie.precut.model.text.BundledFont
import com.savie.precut.model.totalDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInTemplatesTest {
    private val all = BuiltInTemplates.all

    @Test
    fun `twelve templates, two in every category, unique ids`() {
        assertEquals(12, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.all { it.id.startsWith(BuiltInTemplates.PREFIX) })
        TemplateCategory.entries.filter { it != TemplateCategory.OTHER }.forEach { category ->
            assertEquals("$category", 2, all.count { it.category == category })
        }
    }

    @Test
    fun `every template is well formed`() {
        val filters = FilterCatalog.presets.map { it.id }.toSet()
        val fonts = BundledFont.entries.map { it.id }.toSet()
        for (template in all) {
            val project = template.project
            val total = project.totalDurationMs()
            assertTrue("${template.id}: ${total}ms", total in 4_000..30_000)
            assertTrue("${template.id} has slots", template.slots.isNotEmpty())
            template.slots.forEachIndexed { index, slot ->
                assertEquals(SLOT_URI_PREFIX + index, project.clipById(slot.clipId)?.uri)
            }
            project.clips.mapNotNull { it.look.filterId }.forEach { assertTrue("${template.id}: filter $it", it in filters) }
            project.textOverlays.forEach { text ->
                assertTrue("${template.id}: ${text.text} ends at ${text.endMs} of $total", text.startMs >= 0 && text.endMs <= total)
                text.fontId?.let { assertTrue("${template.id}: font $it", it in fonts) }
            }
            assertEquals(project.textOverlays.size, project.textOverlays.map { it.id }.toSet().size)
            assertTrue(project.effectLayers.all { it.endMs <= total })
        }
    }

    @Test
    fun `every template saves, loads and fills`() {
        val pick = TemplatePick("content://clip", MediaInfo(ClipType.VIDEO, 20_000, 1080, 1920, 0))
        for (template in all) {
            assertEquals(template.id, template, TemplateSerializer.decode(TemplateSerializer.encode(template)))
            val filled = template.fill(listOf(pick), "p", 0).project
            assertTrue(template.id, filled.clips.none { it.uri.startsWith(SLOT_URI_PREFIX) })
            assertEquals(template.id, template.project.totalDurationMs(), filled.totalDurationMs())
        }
        assertNotNull(BuiltInTemplates.byId(all.first().id))
    }
}
