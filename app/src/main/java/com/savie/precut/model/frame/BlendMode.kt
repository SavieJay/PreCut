package com.savie.precut.model.frame

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * How an overlay mixes with what's under it (CapCut's Blend), per colour channel in 0..1 with the
 * W3C compositing formulas. The overlay's alpha then mixes the result with the backdrop:
 * `backdrop * (1 - alpha) + mix(backdrop, source) * alpha`. Main-track clips are always [NORMAL].
 * `LayerShaders.BLEND` runs the same formulas on the GPU (`id` picks the branch and never changes).
 */
@Serializable
enum class BlendMode(val id: Int) {
    NORMAL(0),
    MULTIPLY(1),
    SCREEN(2),
    OVERLAY(3),
    DARKEN(4),
    LIGHTEN(5),
    ADD(6),
    SOFT_LIGHT(7),
    DIFFERENCE(8);

    /** One channel of [source] (the overlay) over [backdrop], before alpha. */
    fun mix(backdrop: Float, source: Float): Float = when (this) {
        NORMAL -> source
        MULTIPLY -> backdrop * source
        SCREEN -> screen(backdrop, source)
        OVERLAY -> hardLight(source = backdrop, backdrop = source)
        DARKEN -> minOf(backdrop, source)
        LIGHTEN -> maxOf(backdrop, source)
        ADD -> minOf(1f, backdrop + source)
        SOFT_LIGHT -> softLight(backdrop, source)
        DIFFERENCE -> abs(backdrop - source)
    }

    /** The composited channel with the overlay's [alpha] (straight, 0..1). */
    fun composite(backdrop: Float, source: Float, alpha: Float): Float = backdrop * (1f - alpha) + mix(backdrop, source) * alpha
}

// Not in a companion: kotlinx.serialization gives a @Serializable enum its own.
private fun screen(backdrop: Float, source: Float) = backdrop + source - backdrop * source

private fun hardLight(backdrop: Float, source: Float) =
    if (source <= 0.5f) backdrop * 2f * source else screen(backdrop, 2f * source - 1f)

private fun softLight(backdrop: Float, source: Float): Float {
    if (source <= 0.5f) return backdrop - (1f - 2f * source) * backdrop * (1f - backdrop)
    val d = if (backdrop <= 0.25f) ((16f * backdrop - 12f) * backdrop + 4f) * backdrop else sqrt(backdrop)
    return backdrop + (2f * source - 1f) * (d - backdrop)
}
