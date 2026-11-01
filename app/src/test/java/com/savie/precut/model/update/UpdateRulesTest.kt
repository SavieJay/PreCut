package com.savie.precut.model.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRulesTest {
    private val config = AppConfig(minSupportedVersionCode = 40, latestVersionCode = 42, forceMessage = "Please update", whatsNew = listOf("AI edit"), downloadUrl = "https://example.com/get")

    @Test
    fun `below the minimum blocks, with the server's message and download page`() {
        assertEquals(UpdatePrompt.Required("Please update", "https://example.com/get"), updatePrompt(39, config, exporting = false))
        assertEquals(UpdatePrompt.Required(null, null), updatePrompt(39, config.copy(forceMessage = " ", downloadUrl = ""), exporting = false))
    }

    @Test
    fun `between the minimum and the latest offers the update`() {
        assertEquals(UpdatePrompt.Available, updatePrompt(40, config, exporting = false))
        assertEquals(UpdatePrompt.Available, updatePrompt(41, config, exporting = true))
    }

    @Test
    fun `the latest version, or zeros, shows nothing`() {
        assertEquals(UpdatePrompt.None, updatePrompt(42, config, exporting = false))
        assertEquals(UpdatePrompt.None, updatePrompt(50, config, exporting = false))
        assertEquals(UpdatePrompt.None, updatePrompt(1, AppConfig(), exporting = false))
    }

    @Test
    fun `offline with nothing kept never blocks`() {
        assertEquals(UpdatePrompt.None, updatePrompt(1, null, exporting = false))
    }

    @Test
    fun `a running export is never interrupted, and the block comes after it`() {
        assertEquals(UpdatePrompt.None, updatePrompt(39, config, exporting = true))
        assertTrue(updatePrompt(39, config, exporting = false) is UpdatePrompt.Required)
    }

    @Test
    fun `what's new shows once after an update, not on a fresh install or for a later release`() {
        assertEquals(listOf("AI edit"), whatsNewAfterUpdate(42, lastRunVersionCode = 38, config))
        assertEquals(emptyList<String>(), whatsNewAfterUpdate(42, lastRunVersionCode = null, config))
        assertEquals(emptyList<String>(), whatsNewAfterUpdate(42, lastRunVersionCode = 42, config))
        // The bullets describe 42; a phone that only reached 41 doesn't get them.
        assertEquals(emptyList<String>(), whatsNewAfterUpdate(41, lastRunVersionCode = 38, config))
        assertEquals(emptyList<String>(), whatsNewAfterUpdate(42, lastRunVersionCode = 38, null))
        assertEquals(emptyList<String>(), whatsNewAfterUpdate(42, lastRunVersionCode = 38, config.copy(whatsNew = listOf(" "))))
    }

    @Test
    fun `checks are at most every 15 minutes, and a clock moved back checks again`() {
        assertTrue(shouldCheck(nowMs = 1_000L, lastCheckMs = null))
        assertFalse(shouldCheck(nowMs = 10 * 60_000L, lastCheckMs = 0L))
        assertTrue(shouldCheck(nowMs = 15 * 60_000L, lastCheckMs = 0L))
        assertTrue(shouldCheck(nowMs = 0L, lastCheckMs = 60_000L))
    }
}
