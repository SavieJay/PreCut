package com.savie.precut.model.frame

import org.junit.Assert.assertEquals
import org.junit.Test

class BlendModeTest {

    private fun assertMix(expected: Float, mode: BlendMode, backdrop: Float, source: Float) =
        assertEquals("$mode($backdrop, $source)", expected, mode.mix(backdrop, source), 1e-5f)

    @Test
    fun formulas() {
        assertMix(0.3f, BlendMode.NORMAL, 0.8f, 0.3f)
        assertMix(0.24f, BlendMode.MULTIPLY, 0.8f, 0.3f)
        assertMix(0.86f, BlendMode.SCREEN, 0.8f, 0.3f)
        assertMix(0.3f, BlendMode.DARKEN, 0.8f, 0.3f)
        assertMix(0.8f, BlendMode.LIGHTEN, 0.8f, 0.3f)
        assertMix(1f, BlendMode.ADD, 0.8f, 0.3f)
        assertMix(0.5f, BlendMode.DIFFERENCE, 0.8f, 0.3f)
        // Overlay is hard light with the layers swapped: a dark backdrop multiplies, a light one screens.
        assertMix(2f * 0.2f * 0.6f, BlendMode.OVERLAY, 0.2f, 0.6f)
        assertMix(1f - 2f * (1f - 0.8f) * (1f - 0.6f), BlendMode.OVERLAY, 0.8f, 0.6f)
    }

    @Test
    fun softLight_isNeutralAtMidGrey_andDarkensOrLightens() {
        for (b in listOf(0.1f, 0.4f, 0.9f)) assertMix(b, BlendMode.SOFT_LIGHT, b, 0.5f)
        assert(BlendMode.SOFT_LIGHT.mix(0.5f, 0.2f) < 0.5f)
        assert(BlendMode.SOFT_LIGHT.mix(0.5f, 0.8f) > 0.5f)
    }

    @Test
    fun alphaMixesTheResultWithTheBackdrop() {
        assertEquals(0.8f, BlendMode.MULTIPLY.composite(0.8f, 0.3f, alpha = 0f), 1e-5f)
        assertEquals(0.24f, BlendMode.MULTIPLY.composite(0.8f, 0.3f, alpha = 1f), 1e-5f)
        assertEquals(0.52f, BlendMode.MULTIPLY.composite(0.8f, 0.3f, alpha = 0.5f), 1e-5f)
    }

    @Test
    fun shaderIdsAreStable() {
        assertEquals((0..8).toList(), BlendMode.entries.map { it.id })
    }
}
