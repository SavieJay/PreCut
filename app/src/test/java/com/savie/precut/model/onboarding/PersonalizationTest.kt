package com.savie.precut.model.onboarding

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.template.TemplateCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationTest {

    @Test
    fun `goals pick the starting aspect ratio, social winning a mix`() {
        assertEquals(AspectRatio.RATIO_9_16, defaultAspectRatio(setOf(CreationGoal.SOCIAL, CreationGoal.YOUTUBE)))
        assertEquals(AspectRatio.RATIO_16_9, defaultAspectRatio(setOf(CreationGoal.YOUTUBE)))
        assertEquals(AspectRatio.RATIO_1_1, defaultAspectRatio(setOf(CreationGoal.BUSINESS, CreationGoal.MEMORIES)))
        assertEquals(AspectRatio.RATIO_9_16, defaultAspectRatio(setOf(CreationGoal.MEMORIES)))
        assertNull(defaultAspectRatio(emptySet()))
    }

    @Test
    fun `goals put their template categories first, without repeats`() {
        assertEquals(
            listOf(TemplateCategory.SOCIAL, TemplateCategory.VLOG, TemplateCategory.BUSINESS),
            preferredTemplateCategories(setOf(CreationGoal.BUSINESS, CreationGoal.YOUTUBE, CreationGoal.SOCIAL)),
        )
        val ordered = listOf(TemplateCategory.TRAVEL, TemplateCategory.BUSINESS, TemplateCategory.OTHER, TemplateCategory.SOCIAL)
            .preferringCategories(listOf(TemplateCategory.SOCIAL, TemplateCategory.BUSINESS)) { it }
        assertEquals(listOf(TemplateCategory.SOCIAL, TemplateCategory.BUSINESS, TemplateCategory.TRAVEL, TemplateCategory.OTHER), ordered)
    }

    @Test
    fun `only new editors start in simple mode`() {
        assertTrue(simpleModeFor(Experience.NEW))
        assertFalse(simpleModeFor(Experience.SOME))
        assertFalse(simpleModeFor(Experience.PRO))
    }
}
