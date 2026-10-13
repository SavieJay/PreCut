package com.savie.precut.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputSizeTest {
    @Test
    fun shortSideIs1080_andDimensionsAreEven() {
        assertEquals(OutputSize(1080, 1920), outputSizeFor(AspectRatio.RATIO_9_16))
        assertEquals(OutputSize(1920, 1080), outputSizeFor(AspectRatio.RATIO_16_9))
        assertEquals(OutputSize(1080, 1080), outputSizeFor(AspectRatio.RATIO_1_1))
        assertEquals(OutputSize(1080, 1350), outputSizeFor(AspectRatio.RATIO_4_5))
        assertEquals(OutputSize(720, 1280), outputSizeFor(AspectRatio.RATIO_9_16, shortSidePx = 720))
    }

    @Test
    fun oddResults_areRoundedDownToEven() {
        // 721 * 16 / 9 = 1281.7 -> 1281 -> 1280; 721 -> 720
        assertEquals(OutputSize(720, 1280), outputSizeFor(AspectRatio.RATIO_9_16, shortSidePx = 721))
    }
}
