package com.savie.precut.media.look

import com.savie.precut.model.look.Adjustments
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.look.CurvePoint
import com.savie.precut.model.look.Curves
import com.savie.precut.model.look.HslBand
import com.savie.precut.model.look.HslColor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookUniformsTest {

    @Test
    fun identityLook_isNeutral() {
        val u = LookUniforms.of(ClipLook())
        assertArrayEquals(FloatArray(4), u.adjustA, 0f)
        assertEquals(0f, u.lutMix)
        assertFalse(u.hslEnabled)
        assertNull(u.curveTable)
    }

    @Test
    fun adjustmentsAndFilter_mapToShaderRanges() {
        val look = ClipLook(
            filterId = "basic.warm", filterIntensity = 0.6f,
            adjust = Adjustments(exposure = 100f, contrast = -50f, hue = 50f, vignette = 40f, sharpen = 20f),
        )
        val u = LookUniforms.of(look)
        assertEquals(LookUniforms.EXPOSURE_STOPS, u.adjustA[0])
        assertEquals(-0.5f, u.adjustA[2])
        assertEquals(30f, u.adjustB[2]) // ±100 → ±60°
        assertEquals(0.4f, u.adjustC[2])
        assertEquals(0.2f, u.sharpen)
        assertEquals(0.6f, u.lutMix)
    }

    @Test
    fun hslBands_packInColourOrder() {
        val bands = ClipLook.DEFAULT_HSL.toMutableList().also { it[HslColor.BLUE.ordinal] = HslBand(hue = 100f, saturation = 50f) }
        val u = LookUniforms.of(ClipLook(hsl = bands))
        assertTrue(u.hslEnabled)
        assertEquals(30f, u.hslHue[HslColor.BLUE.ordinal])
        assertEquals(0.5f, u.hslSaturation[HslColor.BLUE.ordinal])
    }

    @Test
    fun curves_foldMasterIntoEachChannel() {
        // Master: invert. Red: identity. So red output at 0 = master(0) = 1.
        val curves = Curves(rgb = listOf(CurvePoint(0f, 1f), CurvePoint(1f, 0f)))
        val table = LookUniforms.of(ClipLook(curves = curves)).curveTable!!
        assertEquals(1f, table[0], 1e-4f)
        assertEquals(0f, table[255 * 3], 1e-4f)
    }
}
