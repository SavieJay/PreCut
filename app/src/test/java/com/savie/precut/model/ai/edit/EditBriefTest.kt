package com.savie.precut.model.ai.edit

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditBriefTest {

    @Test
    fun `items get short aliases in timeline order, and real ids stay on the phone`() {
        val aliases = IdAliases()
        val brief = editProject().editBrief(aliases, 0L, setOf("title"))
        val clips = brief.getValue("clips").jsonArray.map { it.jsonObject }
        assertEquals(listOf("c1", "c2", "c3", "c4"), clips.map { it.getValue("id").jsonPrimitive.content })
        assertEquals("v2", aliases.real("c2"))
        assertNull(aliases.real("v2"))
        assertEquals("t1", brief.getValue("selected").jsonArray[0].jsonPrimitive.content)
        assertFalse(brief.toString().contains("content://"))
    }

    @Test
    fun `clips say how they play, photos have no source length`() {
        val clips = editProject().editBrief(IdAliases(), 0L, emptySet()).getValue("clips").jsonArray.map { it.jsonObject }
        assertEquals(10.0, clips[1].getValue("start_seconds").jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(10.0, clips[0].getValue("source_seconds").jsonPrimitive.content.toDouble(), 0.0)
        assertEquals("image", clips[3].getValue("type").jsonPrimitive.content)
        assertFalse(clips[3].containsKey("source_seconds"))
    }

    @Test
    fun `the catalog lists what this app has, and music says its beats`() {
        val brief = editProject().editBrief(IdAliases(), 0L, emptySet())
        val catalog = brief.getValue("catalog").jsonObject
        assertTrue(catalog.getValue("transitions").jsonArray.any { it.jsonPrimitive.content == "dissolve" })
        assertTrue(catalog.getValue("filters").jsonArray.any { it.jsonPrimitive.content == "basic.vivid" })
        assertEquals(40, brief.getValue("audio").jsonArray[0].jsonObject.getValue("beats").jsonPrimitive.content.toInt())
    }

    @Test
    fun `the media section carries loudness, shots and spoken lines`() {
        val facts = MediaFacts(listOf(0.5f, 1f, 0.123f), listOf(Shot(0L, 5_000L, listOf("seashore"))))
        val media = editProject().editBrief(IdAliases(), 0L, emptySet(), facts).getValue("media").jsonObject
        assertEquals(listOf(0.5, 1.0, 0.12), media.getValue("loudness_per_second").jsonArray.map { it.jsonPrimitive.content.toDouble() })
        assertEquals("seashore", media.getValue("shots").jsonArray[0].jsonObject.getValue("seen").jsonArray[0].jsonPrimitive.content)
        assertTrue(media.getValue("lines").jsonArray.isEmpty())
        // Picture changes and clip starts make the shots; slivers under half a second are dropped.
        assertEquals(listOf(0L to 3_000L, 3_200L to 10_000L), shotsFrom(10_000L, listOf(3_000L, 3_200L), listOf(0L)))
    }

    @Test
    fun `ratios round-trip by name`() {
        com.savie.precut.model.AspectRatio.entries.forEach { assertEquals(it, EditCatalog.ratio(EditCatalog.ratioName(it))) }
        assertEquals(0.12, seconds(123L), 0.0)
    }
}
