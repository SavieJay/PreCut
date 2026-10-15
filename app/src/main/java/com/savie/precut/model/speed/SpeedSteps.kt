package com.savie.precut.model.speed

import kotlin.math.floor

/**
 * A speed curve as Media3 plays it: the clip's source split into [speeds].size equal steps, each
 * at one speed. Lengths follow Media3's `SpeedProviderUtil` (sum of step / speed, floored in µs),
 * so the timeline and the player agree. Times are relative to the clip's trim start.
 */
class SpeedSteps(val sourceLengthUs: Long, val speeds: FloatArray) {

    val count: Int get() = speeds.size

    /** Source time where step [index] starts (index == count is the end). */
    fun boundaryUs(index: Int): Long = sourceLengthUs * index / count

    /** The step containing source time [sourceUs]. */
    fun stepAt(sourceUs: Long): Int {
        if (sourceUs <= 0L) return 0
        val index = (sourceUs * count / sourceLengthUs.coerceAtLeast(1L)).toInt().coerceIn(0, count - 1)
        // Integer boundaries can put an exact boundary one step early.
        return if (index + 1 < count && sourceUs >= boundaryUs(index + 1)) index + 1 else index
    }

    /** Output length of the whole clip, in µs. */
    fun timelineLengthUs(): Long = floor(timelineAtUs(sourceLengthUs)).toLong()

    /** Output time (µs, unfloored) when the source reaches [sourceUs]. */
    fun timelineAtUs(sourceUs: Long): Double {
        val end = sourceUs.coerceIn(0L, sourceLengthUs)
        var total = 0.0
        for (i in 0 until count) {
            val from = boundaryUs(i)
            if (from >= end) break
            total += (minOf(boundaryUs(i + 1), end) - from) / speeds[i].toDouble()
        }
        return total
    }

    /** Source time (µs) shown at output time [timelineUs]. */
    fun sourceAtUs(timelineUs: Double): Long {
        var elapsed = 0.0
        for (i in 0 until count) {
            val from = boundaryUs(i)
            val step = (boundaryUs(i + 1) - from) / speeds[i].toDouble()
            if (timelineUs < elapsed + step) return from + ((timelineUs - elapsed).coerceAtLeast(0.0) * speeds[i]).toLong()
            elapsed += step
        }
        return sourceLengthUs
    }

    /** The highest speed (for the frame-rate cap). */
    fun maxSpeed(): Float = speeds.maxOrNull() ?: 1f

    companion object {
        /** Steps per clip: fine enough to look smooth, few enough to keep seeking cheap. */
        const val STEPS = 40

        /** [curve] sampled at each step's middle over a source of [sourceLengthUs]. */
        fun of(curve: SpeedCurve, sourceLengthUs: Long): SpeedSteps =
            SpeedSteps(sourceLengthUs, FloatArray(STEPS) { i -> curve.speedAt((i + 0.5f) / STEPS) })
    }
}
