package com.savie.precut.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun formatsMinutesAndHours() {
        assertEquals("0:00", formatDurationMs(0L))
        assertEquals("0:00", formatDurationMs(-5L))
        assertEquals("0:09", formatDurationMs(9_999L))
        assertEquals("1:05", formatDurationMs(65_000L))
        assertEquals("1:00:00", formatDurationMs(3_600_000L))
        assertEquals("12:03:04", formatDurationMs(43_384_000L))
    }

    @Test
    fun preciseFormat_addsTenths() {
        assertEquals("0:00.0", formatPreciseMs(0L))
        assertEquals("0:03.4", formatPreciseMs(3_450L))
        assertEquals("1:05.9", formatPreciseMs(65_999L))
        assertEquals("0:00.0", formatPreciseMs(-20L))
    }
}
