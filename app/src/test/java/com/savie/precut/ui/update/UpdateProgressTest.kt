package com.savie.precut.ui.update

import com.savie.precut.update.PlayUpdates
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateProgressTest {
    @Test
    fun `the update screens follow Play's background download`() {
        assertEquals(UpdateProgress.Idle, updateProgress(fraction = null, downloaded = false))
        assertEquals(UpdateProgress.Downloading(0.25f), updateProgress(fraction = 0.25f, downloaded = false))
        assertEquals(UpdateProgress.Ready, updateProgress(fraction = null, downloaded = true))
    }

    @Test
    fun `download progress is a fraction, zero until Play knows the size`() {
        assertEquals(0.5f, PlayUpdates.downloadFraction(5_000L, 10_000L))
        assertEquals(0f, PlayUpdates.downloadFraction(5_000L, 0L))
        assertEquals(1f, PlayUpdates.downloadFraction(12_000L, 10_000L))
    }
}
