package com.savie.precut.model.growth

import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.export.END_CARD_MS
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.export.withEndCard
import com.savie.precut.model.project
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthTest {
    private val day = 24L * 60 * 60 * 1_000

    @Test
    fun `the review is asked after a few good exports, then rarely`() {
        assertFalse(shouldAskForReview(nowMs = 0L, lastAskedMs = null, successfulExports = 1))
        assertTrue(shouldAskForReview(nowMs = 0L, lastAskedMs = null, successfulExports = 2))
        assertFalse(shouldAskForReview(nowMs = 30 * day, lastAskedMs = 0L, successfulExports = 9))
        assertTrue(shouldAskForReview(nowMs = 45 * day, lastAskedMs = 0L, successfulExports = 9))
    }

    @Test
    fun `the end card is off by default and adds two seconds when on`() {
        assertFalse(ExportSettings().endCard)
        val base = project(videoClip("a"))
        val withCard = base.withEndCard("file:///end.png", MediaInfo(ClipType.IMAGE, 0L, 1080, 1920, 0))
        assertEquals(base.totalDurationMs() + END_CARD_MS, withCard.totalDurationMs())
        assertEquals("file:///end.png", withCard.clips.last().uri)
    }
}
