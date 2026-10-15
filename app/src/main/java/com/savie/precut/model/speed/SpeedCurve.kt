package com.savie.precut.model.speed

import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.exp

const val MIN_CURVE_SPEED = 0.1f
const val MAX_CURVE_SPEED = 10f

/** A point of a speed curve: [x] is the fraction of the clip's (trimmed) source, 0..1. */
@Serializable
data class SpeedPoint(val x: Float, val speed: Float)

/** Speed curve shapes (T-03). [CUSTOM] is any hand-edited curve. */
@Serializable
enum class SpeedPreset { CUSTOM, MONTAGE, HERO, BULLET, JUMP_CUT, FLASH_IN, FLASH_OUT }

/**
 * A clip's speed across its source, through [points] (sorted, first at x 0 and last at x 1).
 * Between points the speed changes evenly on a log scale, as the curve graph draws it.
 */
@Serializable
data class SpeedCurve(val points: List<SpeedPoint>, val preset: SpeedPreset = SpeedPreset.CUSTOM) {

    /** The speed at source fraction [x]. */
    fun speedAt(x: Float): Float {
        val p = points
        if (p.isEmpty()) return 1f
        if (x <= p.first().x) return p.first().speed
        for (i in 1 until p.size) {
            if (x <= p[i].x) {
                val a = p[i - 1]
                val b = p[i]
                val t = if (b.x > a.x) (x - a.x) / (b.x - a.x) else 1f
                return exp(ln(a.speed) + (ln(b.speed) - ln(a.speed)) * t)
            }
        }
        return p.last().speed
    }

    /** Sorted, clamped, with fixed ends at x 0 and 1; at least two points. */
    fun normalized(): SpeedCurve {
        val inner = points.filter { it.x > 0f && it.x < 1f }.sortedBy { it.x }
        val first = points.minByOrNull { it.x }?.speed ?: 1f
        val last = points.maxByOrNull { it.x }?.speed ?: 1f
        val all = listOf(SpeedPoint(0f, first)) + inner + SpeedPoint(1f, last)
        return copy(points = all.map { it.copy(speed = it.speed.coerceIn(MIN_CURVE_SPEED, MAX_CURVE_SPEED)) })
    }

    /** The part of the curve between source fractions [from] and [to], stretched to 0..1 (for splitting). */
    fun slice(from: Float, to: Float): SpeedCurve {
        val span = (to - from).coerceAtLeast(1e-6f)
        val inside = points.filter { it.x > from && it.x < to }.map { it.copy(x = (it.x - from) / span) }
        return copy(points = listOf(SpeedPoint(0f, speedAt(from))) + inside + SpeedPoint(1f, speedAt(to)))
    }

    companion object {
        fun preset(preset: SpeedPreset): SpeedCurve = SpeedCurve(
            when (preset) {
                SpeedPreset.CUSTOM -> listOf(p(0f, 1f), p(0.25f, 1f), p(0.5f, 1f), p(0.75f, 1f), p(1f, 1f))
                SpeedPreset.MONTAGE -> listOf(p(0f, 1f), p(0.2f, 3f), p(0.5f, 0.5f), p(0.8f, 3f), p(1f, 1f))
                SpeedPreset.HERO -> listOf(p(0f, 3f), p(0.35f, 3f), p(0.5f, 0.2f), p(0.65f, 3f), p(1f, 3f))
                SpeedPreset.BULLET -> listOf(p(0f, 2f), p(0.4f, 0.3f), p(0.6f, 0.3f), p(1f, 2f))
                SpeedPreset.JUMP_CUT -> listOf(p(0f, 1f), p(0.4f, 1f), p(0.5f, 8f), p(0.6f, 1f), p(1f, 1f))
                SpeedPreset.FLASH_IN -> listOf(p(0f, 6f), p(0.4f, 2f), p(1f, 1f))
                SpeedPreset.FLASH_OUT -> listOf(p(0f, 1f), p(0.6f, 2f), p(1f, 6f))
            },
            preset,
        )

        private fun p(x: Float, speed: Float) = SpeedPoint(x, speed)
    }
}
