package com.savie.precut.media.look

import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.look.ColorMath
import com.savie.precut.model.look.CurveChannel
import com.savie.precut.model.look.HslColor

/**
 * A [ClipLook] turned into the look shader's uniforms (pure, so the mapping is unit-tested).
 * Scalars are packed into vec4s to stay well under GLES 2's uniform limits.
 */
class LookUniforms private constructor(
    /** exposure (stops), brightness, contrast (-1..1), saturation (-1..1). */
    val adjustA: FloatArray,
    /** temperature, tint, hue (degrees), highlights. */
    val adjustB: FloatArray,
    /** shadows, fade, vignette, grain (0..1 each, shadows -1..1). */
    val adjustC: FloatArray,
    /** sharpen (0..1), LUT mix (0..1). */
    val sharpen: Float,
    val lutMix: Float,
    /** Per band: hue shift (degrees), saturation shift (-1..1), lightness shift (-1..1); bands 0–3 then 4–7. */
    val hslHue: FloatArray,
    val hslSaturation: FloatArray,
    val hslLightness: FloatArray,
    val hslEnabled: Boolean,
    /** 256 RGB entries (0..1), the master curve folded into each channel; null when curves are identity. */
    val curveTable: FloatArray?,
) {
    companion object {
        /** Hue ±100 on a band (or on the global hue slider) shifts by this many degrees. */
        const val BAND_HUE_DEGREES = 30f
        const val GLOBAL_HUE_DEGREES = 60f
        const val EXPOSURE_STOPS = 1.5f

        fun of(look: ClipLook): LookUniforms {
            val a = look.adjust
            val bands = look.hsl.takeIf { it.size == HslColor.entries.size } ?: ClipLook.DEFAULT_HSL
            return LookUniforms(
                adjustA = floatArrayOf(a.exposure / 100f * EXPOSURE_STOPS, a.brightness / 100f, a.contrast / 100f, a.saturation / 100f),
                adjustB = floatArrayOf(a.temperature / 100f, a.tint / 100f, a.hue / 100f * GLOBAL_HUE_DEGREES, a.highlights / 100f),
                adjustC = floatArrayOf(a.shadows / 100f, a.fade / 100f, a.vignette / 100f, a.grain / 100f),
                sharpen = a.sharpen / 100f,
                lutMix = if (look.hasFilter) look.filterIntensity.coerceIn(0f, 1f) else 0f,
                hslHue = FloatArray(8) { bands[it].hue / 100f * BAND_HUE_DEGREES },
                hslSaturation = FloatArray(8) { bands[it].saturation / 100f },
                hslLightness = FloatArray(8) { bands[it].lightness / 100f },
                hslEnabled = bands.any { !it.isIdentity },
                curveTable = if (look.curves.isIdentity) null else curveTable(look),
            )
        }

        private fun curveTable(look: ClipLook): FloatArray {
            val master = ColorMath.curveTable(look.curves[CurveChannel.RGB])
            val channels = listOf(CurveChannel.RED, CurveChannel.GREEN, CurveChannel.BLUE).map { ColorMath.curveTable(look.curves[it]) }
            return FloatArray(256 * 3) { i ->
                val entry = i / 3
                ColorMath.sample(channels[i % 3], master[entry])
            }
        }
    }
}
