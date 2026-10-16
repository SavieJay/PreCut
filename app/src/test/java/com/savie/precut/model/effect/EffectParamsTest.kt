package com.savie.precut.model.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectParamsTest {

    @Test
    fun everyType_hasFourParamsAndAUniqueShaderBranch() {
        EffectType.entries.forEach { type ->
            assertEquals(type.name, 4, EffectParams.of(EffectSettings(type)).size)
        }
        val ids = EffectType.entries.map { it.shaderId }
        assertEquals(ids.size, ids.toSet().size)
        // The shader's branches are 1..28 in order; the last one is the split screen.
        assertEquals((1..EffectType.entries.size).toList(), ids.sorted())
        assertTrue(EffectType.entries.filter { it.category == EffectCategory.SPLIT }.all { it.shaderId >= EffectType.SPLIT_2.shaderId })
    }

    @Test
    fun intensity_scalesTheEffect() {
        fun blur(i: Float) = EffectParams.of(EffectSettings(EffectType.BLUR, intensity = i))[0]
        assertTrue(blur(1f) > blur(0.5f) && blur(0.5f) > blur(0f))
        // Pixelate: stronger means fewer, bigger cells.
        fun cells(i: Float) = EffectParams.of(EffectSettings(EffectType.PIXELATE, intensity = i))[0]
        assertEquals(120f, cells(0f))
        assertEquals(14f, cells(1f))
        // Out-of-range intensity is clamped.
        assertEquals(blur(1f), blur(3f))
    }

    @Test
    fun splitScreens_mapToTheirGrids() {
        fun grid(type: EffectType) = EffectParams.of(EffectSettings(type)).let { it[0] to it[1] }
        assertEquals(1f to 2f, grid(EffectType.SPLIT_2))
        assertEquals(1f to 3f, grid(EffectType.SPLIT_3))
        assertEquals(2f to 2f, grid(EffectType.SPLIT_4))
        assertEquals(3f to 3f, grid(EffectType.SPLIT_9))
    }

    @Test
    fun kaleidoscope_hasAnEvenNumberOfSegments() {
        listOf(0f, 0.3f, 0.65f, 1f).forEach { i ->
            val segments = EffectParams.of(EffectSettings(EffectType.KALEIDOSCOPE, intensity = i))[0]
            assertEquals(0f, segments % 2f)
            assertTrue(segments in 4f..12f)
        }
    }

    @Test
    fun time_runsFromTheStartAtTheEffectsSpeed() {
        assertEquals(0f, EffectParams.timeSeconds(2_000_000L, 2_000_000L, 1f))
        assertEquals(1.5f, EffectParams.timeSeconds(3_500_000L, 2_000_000L, 1f), 1e-6f)
        assertEquals(3f, EffectParams.timeSeconds(3_500_000L, 2_000_000L, 2f), 1e-6f)
        // Before the start it stays at 0; speed is clamped.
        assertEquals(0f, EffectParams.timeSeconds(1_000_000L, 2_000_000L, 1f))
        assertEquals(EffectParams.timeSeconds(3_000_000L, 0L, MAX_EFFECT_SPEED), EffectParams.timeSeconds(3_000_000L, 0L, 10f))
    }

    @Test
    fun layers_areActiveOnlyInTheirRange() {
        assertFalse(EffectParams.isActive(999_999L, 1_000_000L, 4_000_000L))
        assertTrue(EffectParams.isActive(1_000_000L, 1_000_000L, 4_000_000L))
        assertTrue(EffectParams.isActive(3_999_999L, 1_000_000L, 4_000_000L))
        assertFalse(EffectParams.isActive(4_000_000L, 1_000_000L, 4_000_000L))
        // A clip's own effect has no range.
        assertTrue(EffectParams.isActive(123L, null, null))
    }

    @Test
    fun settings_normalizeIntoRange() {
        assertEquals(EffectSettings(EffectType.VHS, 1f, MIN_EFFECT_SPEED), EffectSettings(EffectType.VHS, 2f, 0f).normalized())
    }
}
