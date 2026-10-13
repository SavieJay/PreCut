package com.savie.precut.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexColorTest {
    @Test
    fun parsesSixAndEightDigits_withOrWithoutHash() {
        assertEquals(0xFFFF4D6D.toInt(), parseHexColor("#FF4D6D"))
        assertEquals(0xFFFF4D6D.toInt(), parseHexColor("ff4d6d"))
        assertEquals(0x80FF0000.toInt(), parseHexColor("#80FF0000"))
        assertNull(parseHexColor("#FFF"))
        assertNull(parseHexColor("zzzzzz"))
    }

    @Test
    fun formatsWithoutAlpha() {
        assertEquals("#FF4D6D", formatHexColor(0x80FF4D6D.toInt()))
    }
}
