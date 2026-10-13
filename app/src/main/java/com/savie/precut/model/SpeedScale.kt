package com.savie.precut.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** Clip speed range (CapCut's normal speed). */
const val MIN_CLIP_SPEED = 0.1f
const val MAX_CLIP_SPEED = 100f

/** Audio clip speed range. */
const val MIN_AUDIO_SPEED = 0.25f
const val MAX_AUDIO_SPEED = 4f

/**
 * Logarithmic speed slider: position 0..1 maps to [min]..[max] so every doubling is the same
 * distance (for clips, 1x sits a third of the way along).
 */
class SpeedScale(val min: Float, val max: Float) {
    private val logMin = ln(min.toDouble())
    private val logRange = ln(max.toDouble()) - logMin

    fun toSlider(speed: Float): Float = ((ln(speed.coerceIn(min, max).toDouble()) - logMin) / logRange).toFloat()

    /** The speed at slider [position], rounded to a readable step (0.05x below 1x, 0.1x below 10x, then 1x). */
    fun fromSlider(position: Float): Float {
        val raw = exp(logMin + position.coerceIn(0f, 1f) * logRange).toFloat()
        val rounded = when {
            raw < 1f -> (raw * 20).roundToInt() / 20f
            raw < 10f -> (raw * 10).roundToInt() / 10f
            else -> raw.roundToInt().toFloat()
        }
        return rounded.coerceIn(min, max)
    }

    companion object {
        val CLIP = SpeedScale(MIN_CLIP_SPEED, MAX_CLIP_SPEED)
        val AUDIO = SpeedScale(MIN_AUDIO_SPEED, MAX_AUDIO_SPEED)
    }
}
