package com.savie.precut.data

import com.savie.precut.model.project
import com.savie.precut.model.template.TemplateCategory
import com.savie.precut.model.template.toTemplate
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateSerializerTest {

    private val template = project(videoClip("a"), videoClip("b")).toTemplate("t", "Promo", TemplateCategory.BUSINESS, setOf("a"), emptySet(), 3)

    @Test
    fun `round trip`() {
        assertEquals(template, TemplateSerializer.decode(TemplateSerializer.encode(template)))
    }

    @Test
    fun `a newer schema or corrupt JSON gives null`() {
        assertNull(TemplateSerializer.decode(TemplateSerializer.encode(template).replaceFirst("{", "{\"schema\":2,")))
        assertNull(TemplateSerializer.decode("{not json"))
        assertNull(TemplateSerializer.decode("[]"))
    }

    @Test
    fun `an old embedded project is upgraded like a draft`() {
        val json = """{"id":"t","title":"Old","slots":[{"clipId":"a"}],"project":{"id":"t","name":"Old","createdAt":1,"updatedAt":1,""" +
            """"clips":[{"id":"a","uri":"template://slot/0","type":"VIDEO","sourceDurationMs":5000,"trimEndMs":5000,"filter":"WARM"}]}}"""
        assertEquals("basic.warm", TemplateSerializer.decode(json)?.project?.clips?.single()?.look?.filterId)
    }
}
