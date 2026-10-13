package com.savie.precut.model.look

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    @Test
    fun hsl_roundTrips() {
        listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0.2f, 0.6f, 0.9f), floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.9f, 0.8f, 0.1f)).forEach { rgb ->
            val hsl = ColorMath.rgbToHsl(rgb[0], rgb[1], rgb[2])
            assertArrayEquals(rgb, ColorMath.hslToRgb(hsl[0], hsl[1], hsl[2]), 1e-4f)
        }
        assertArrayEquals(floatArrayOf(240f, 1f, 0.5f), ColorMath.rgbToHsl(0f, 0f, 1f), 1e-4f)
    }

    @Test
    fun identityCurve_isIdentity() {
        val table = ColorMath.curveTable(Curves.IDENTITY)
        table.forEachIndexed { i, y -> assertEquals(i / 255f, y, 1e-4f) }
    }

    @Test
    fun sCurve_passesThroughPoints_isMonotone_andStaysInRange() {
        val points = listOf(CurvePoint(0f, 0f), CurvePoint(0.25f, 0.15f), CurvePoint(0.75f, 0.9f), CurvePoint(1f, 1f))
        val table = ColorMath.curveTable(points, 101)
        assertEquals(0.15f, table[25], 1e-3f)
        assertEquals(0.9f, table[75], 1e-3f)
        for (i in 1 until table.size) assertTrue("dip at $i", table[i] >= table[i - 1] - 1e-6f)
        assertTrue(table.all { it in 0f..1f })
    }

    @Test
    fun flatEnds_outsideFirstAndLastPoint() {
        val table = ColorMath.curveTable(listOf(CurvePoint(0.2f, 0.3f), CurvePoint(0.8f, 0.7f)), 11)
        assertEquals(0.3f, table[0], 1e-4f)
        assertEquals(0.7f, table[10], 1e-4f)
    }
}
