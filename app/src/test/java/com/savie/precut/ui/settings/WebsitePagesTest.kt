package com.savie.precut.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class WebsitePagesTest {
    @Test
    fun aboutLinksPointAtTheWebsitesPages() {
        val pages = WebsitePages("precut.netlify.app")
        assertEquals("https://precut.netlify.app/privacy", pages.privacy)
        assertEquals("https://precut.netlify.app/terms", pages.terms)
        assertEquals("https://precut.netlify.app/support", pages.support)
    }
}
