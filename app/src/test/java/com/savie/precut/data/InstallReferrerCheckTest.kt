package com.savie.precut.data

import com.savie.precut.model.project
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallReferrerCheckTest {
    private val fromTemplatePage = InstallReferrerSource { "utm_source=website&utm_medium=template&utm_campaign=template&template=AbCd1234" }

    @Test
    fun `a fresh install from a template page opens the template, once`() = runBlocking {
        val settings = InMemoryAppSettings()
        val inbox = TemplateLinkInbox()
        val check = InstallReferrerCheck(settings, fromTemplatePage, InMemoryProjectRepository(), inbox)
        check.runOnce()
        assertEquals("AbCd1234", inbox.pending.value)
        assertTrue(settings.values.value.installReferrerChecked)
        inbox.handled()
        check.runOnce()
        assertNull(inbox.pending.value)
    }

    @Test
    fun `someone with projects already (an update) isn't sent to a template`() = runBlocking {
        val inbox = TemplateLinkInbox()
        val projects = InMemoryProjectRepository().apply { save(project()) }
        InstallReferrerCheck(InMemoryAppSettings(), fromTemplatePage, projects, inbox).runOnce()
        assertNull(inbox.pending.value)
    }
}
