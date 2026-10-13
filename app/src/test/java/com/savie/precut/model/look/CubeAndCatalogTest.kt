package com.savie.precut.model.look

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeAndCatalogTest {

    @Test
    fun identityGrade_bakesIdentityCube() {
        val cube = Cube.bake(Grade(), size = 5)
        for (c in listOf(0f, 0.25f, 0.5f, 1f)) {
            val out = cube.lookup(c, 1f - c, c / 2)
            assertEquals(c, out.r, 1e-4f)
            assertEquals(1f - c, out.g, 1e-4f)
            assertEquals(c / 2, out.b, 1e-4f)
        }
    }

    @Test
    fun cubeLookup_matchesGrade_atLatticePoints_andCloseBetween() {
        val grade = FilterCatalog.preset("film.teal_orange")!!.grade
        val cube = Cube.bake(grade)
        val n = (Cube.DEFAULT_SIZE - 1).toFloat()
        val onLattice = listOf(0f, 8f / n, 16f / n, 1f)
        for (v in onLattice) {
            val expected = grade.apply(v, 1f - v, v)
            val actual = cube.lookup(v, 1f - v, v)
            assertEquals(expected.r, actual.r, 1e-4f)
            assertEquals(expected.b, actual.b, 1e-4f)
        }
        val between = cube.lookup(0.37f, 0.52f, 0.61f)
        val exact = grade.apply(0.37f, 0.52f, 0.61f)
        assertEquals(exact.r, between.r, 0.02f)
        assertEquals(exact.g, between.g, 0.02f)
    }

    @Test
    fun catalog_hasUniqueIds_everyCategory_andBakesInRange() {
        val presets = FilterCatalog.presets
        assertTrue(presets.size >= 40)
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        FilterCategory.entries.forEach { assertTrue(it.name, FilterCatalog.inCategory(it).isNotEmpty()) }
        presets.forEach { preset ->
            val cube = Cube.bake(preset.grade, size = 5)
            assertTrue(preset.id, cube.rgb.all { it in 0f..1f })
        }
    }

    @Test
    fun legacyFilters_mapToBasicPresets_withTheirOldValues() {
        FilterCatalog.LEGACY_IDS.values.forEach { assertNotNull(it, FilterCatalog.preset(it)) }
        // Warm was RgbScale(1.1, 1, 0.85).
        val warm = FilterCatalog.preset("basic.warm")!!.grade.apply(0.5f, 0.5f, 0.5f)
        assertEquals(0.55f, warm.r, 1e-4f)
        assertEquals(0.425f, warm.b, 1e-4f)
        // Mono uses Media3's Rec. 709 grayscale weights.
        val mono = FilterCatalog.preset("basic.mono")!!.grade.apply(1f, 0f, 0f)
        assertEquals(0.2126f, mono.g, 1e-4f)
    }

    @Test
    fun texturePixels_layOutBlueSlicesSideBySide() {
        val cube = Cube.bake(Grade(), size = 2)
        val pixels = cube.toTexturePixels() // 4 wide, 2 tall
        assertEquals(0xFF000000.toInt(), pixels[0]) // r0 g0 b0
        assertEquals(0xFFFF0000.toInt(), pixels[1]) // r1 g0 b0
        assertEquals(0xFF0000FF.toInt(), pixels[2]) // r0 g0 b1
        assertEquals(0xFFFFFFFF.toInt(), pixels[7]) // r1 g1 b1
    }
}
