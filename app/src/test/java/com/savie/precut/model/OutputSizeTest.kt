package com.savie.precut.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputSizeTest {
    private fun sized(ratio: AspectRatio, vararg clips: Clip) = project(*clips).copy(aspectRatio = ratio).outputSize()

    @Test
    fun shortSideIs1080_andDimensionsAreEven() {
        assertEquals(OutputSize(1080, 1920), sized(AspectRatio.RATIO_9_16))
        assertEquals(OutputSize(1920, 1080), sized(AspectRatio.RATIO_16_9))
        assertEquals(OutputSize(1080, 1080), sized(AspectRatio.RATIO_1_1))
        assertEquals(OutputSize(1080, 1350), sized(AspectRatio.RATIO_4_5))
        assertEquals(OutputSize(720, 1280), outputSizeFor(9, 16, shortSidePx = 720))
    }

    @Test
    fun capCutRatios() {
        assertEquals(OutputSize(1440, 1080), sized(AspectRatio.RATIO_4_3))
        assertEquals(OutputSize(1080, 1440), sized(AspectRatio.RATIO_3_4))
        assertEquals(OutputSize(2160, 1080), sized(AspectRatio.RATIO_2_1))
        assertEquals(OutputSize(2538, 1080), sized(AspectRatio.RATIO_235_100)) // 2538 = 1080 * 2.35
        assertEquals(OutputSize(1998, 1080), sized(AspectRatio.RATIO_185_100))
        assertEquals(OutputSize(1080, 2340), sized(AspectRatio.RATIO_9_19_5))
    }

    @Test
    fun original_followsFirstClip_afterSourceRotation() {
        val landscape = videoClip("a").copy(sourceWidth = 1920, sourceHeight = 1080)
        assertEquals(OutputSize(1920, 1080), sized(AspectRatio.ORIGINAL, landscape))
        // Phone portrait video: stored 1920x1080 with 90° rotation metadata.
        val rotated = landscape.copy(sourceRotationDegrees = 90)
        assertEquals(OutputSize(1080, 1920), sized(AspectRatio.ORIGINAL, rotated))
    }

    @Test
    fun original_withoutKnownSize_fallsBackTo9x16() {
        assertEquals(OutputSize(1080, 1920), sized(AspectRatio.ORIGINAL))
        assertEquals(OutputSize(1080, 1920), sized(AspectRatio.ORIGINAL, videoClip("a")))
    }

    @Test
    fun oddResults_areRoundedDownToEven() {
        // 721 * 16 / 9 = 1281.7 -> 1281 -> 1280; 721 -> 720
        assertEquals(OutputSize(720, 1280), outputSizeFor(9, 16, shortSidePx = 721))
    }
}
