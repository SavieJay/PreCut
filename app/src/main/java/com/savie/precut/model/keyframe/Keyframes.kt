package com.savie.precut.model.keyframe

import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.look.Adjustments
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** How a keyframe eases into the next one (P-05). */
@Serializable
enum class Easing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, CUSTOM }

/** A cubic Bézier easing through (0, 0), ([x1], [y1]), ([x2], [y2]), (1, 1), like CSS `cubic-bezier`. */
@Serializable
data class EasingCurve(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {

    /** Progress at time fraction [t] (0..1). */
    fun ease(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        // Solve x(s) = t: Newton steps from a good guess, with bisection as the fallback.
        var s = t
        repeat(8) {
            val dx = bezierSlope(s, x1, x2)
            if (abs(dx) < 1e-6f) return@repeat
            s -= (bezier(s, x1, x2) - t) / dx
        }
        if (s !in 0f..1f || abs(bezier(s, x1, x2) - t) > 1e-4f) {
            var lo = 0f
            var hi = 1f
            s = t
            repeat(40) {
                if (bezier(s, x1, x2) < t) lo = s else hi = s
                s = (lo + hi) / 2f
            }
        }
        return bezier(s, y1, y2)
    }

    companion object {
        val LINEAR = EasingCurve(0f, 0f, 1f, 1f)
        val EASE_IN = EasingCurve(0.42f, 0f, 1f, 1f)
        val EASE_OUT = EasingCurve(0f, 0f, 0.58f, 1f)
        val EASE_IN_OUT = EasingCurve(0.42f, 0f, 0.58f, 1f)

        /** A curve with control points kept in range (x in 0..1 so time only moves forward). */
        fun clamped(x1: Float, y1: Float, x2: Float, y2: Float) =
            EasingCurve(x1.coerceIn(0f, 1f), y1.coerceIn(-1f, 2f), x2.coerceIn(0f, 1f), y2.coerceIn(-1f, 2f))

        private fun bezier(s: Float, p1: Float, p2: Float): Float {
            val u = 1f - s
            return 3f * u * u * s * p1 + 3f * u * s * s * p2 + s * s * s
        }

        private fun bezierSlope(s: Float, p1: Float, p2: Float): Float {
            val u = 1f - s
            return 3f * u * u * p1 + 6f * u * s * (p2 - p1) + 3f * s * s * (1f - p2)
        }
    }
}

/** The curve an [easing] follows ([custom] for CUSTOM). */
fun curveOf(easing: Easing, custom: EasingCurve?): EasingCurve = when (easing) {
    Easing.LINEAR -> EasingCurve.LINEAR
    Easing.EASE_IN -> EasingCurve.EASE_IN
    Easing.EASE_OUT -> EasingCurve.EASE_OUT
    Easing.EASE_IN_OUT -> EasingCurve.EASE_IN_OUT
    Easing.CUSTOM -> custom ?: EasingCurve.LINEAR
}

/**
 * A clip keyframe (P-05): every value that can animate, at [atMs] into the clip (timeline time).
 * Between two keyframes each value moves from one to the other along the first one's easing.
 */
@Serializable
data class ClipKeyframe(
    val atMs: Long,
    val transform: ClipTransform = ClipTransform(),
    val opacity: Float = 1f,
    val mask: ClipMask? = null,
    val chromaIntensity: Float? = null,
    val chromaShadow: Float? = null,
    val filterIntensity: Float = 1f,
    val adjust: Adjustments = Adjustments(),
    val volume: Float = 1f,
    /** Into the next keyframe. */
    val easing: Easing = Easing.LINEAR,
    val curve: EasingCurve? = null,
)

/** A text keyframe: position (0..1, y down), size, turn and opacity at [atMs] into the text. */
@Serializable
data class TextKeyframe(
    val atMs: Long,
    val xNorm: Float,
    val yNorm: Float,
    val textSizeSp: Float,
    val rotationDegrees: Float,
    val opacity: Float,
    val easing: Easing = Easing.LINEAR,
    val curve: EasingCurve? = null,
)

/** Keyframes within this distance of the playhead count as "at" it (about one frame). */
const val KEYFRAME_SNAP_MS = 34L

/** Finds the segment around [atMs] in time-sorted [frames]: (before, after, eased progress); one side null outside them. */
fun <T> segmentAt(frames: List<T>, atMs: Long, time: (T) -> Long, easing: (T) -> EasingCurve): Triple<T?, T?, Float> {
    if (frames.isEmpty()) return Triple(null, null, 0f)
    val nextIndex = frames.indexOfFirst { time(it) > atMs }
    if (nextIndex == 0) return Triple(null, frames.first(), 0f)
    if (nextIndex < 0) return Triple(frames.last(), null, 0f)
    val before = frames[nextIndex - 1]
    val after = frames[nextIndex]
    val span = (time(after) - time(before)).coerceAtLeast(1L)
    val t = (atMs - time(before)).toFloat() / span
    return Triple(before, after, easing(before).ease(t))
}

/** The clip's values at [atMs] into it; null without keyframes. */
fun List<ClipKeyframe>.valuesAt(atMs: Long): ClipKeyframe? {
    val (before, after, p) = segmentAt(this, atMs, ClipKeyframe::atMs) { curveOf(it.easing, it.curve) }
    if (before == null) return after?.copy(atMs = atMs)
    if (after == null) return before.copy(atMs = atMs)
    return ClipKeyframe(
        atMs = atMs,
        transform = ClipTransform(
            scale = lerp(before.transform.scale, after.transform.scale, p),
            rotationDegrees = lerp(before.transform.rotationDegrees, after.transform.rotationDegrees, p),
            offsetX = lerp(before.transform.offsetX, after.transform.offsetX, p),
            offsetY = lerp(before.transform.offsetY, after.transform.offsetY, p),
        ),
        opacity = lerp(before.opacity, after.opacity, p),
        mask = lerpMask(before.mask, after.mask, p),
        chromaIntensity = lerpOptional(before.chromaIntensity, after.chromaIntensity, p),
        chromaShadow = lerpOptional(before.chromaShadow, after.chromaShadow, p),
        filterIntensity = lerp(before.filterIntensity, after.filterIntensity, p),
        adjust = lerpAdjust(before.adjust, after.adjust, p),
        volume = lerp(before.volume, after.volume, p),
        easing = before.easing,
        curve = before.curve,
    )
}

/** The text's values at [atMs] into it; null without keyframes. */
fun List<TextKeyframe>.valuesAt(atMs: Long): TextKeyframe? {
    val (before, after, p) = segmentAt(this, atMs, TextKeyframe::atMs) { curveOf(it.easing, it.curve) }
    if (before == null) return after?.copy(atMs = atMs)
    if (after == null) return before.copy(atMs = atMs)
    return TextKeyframe(
        atMs = atMs,
        xNorm = lerp(before.xNorm, after.xNorm, p),
        yNorm = lerp(before.yNorm, after.yNorm, p),
        textSizeSp = lerp(before.textSizeSp, after.textSizeSp, p),
        rotationDegrees = lerp(before.rotationDegrees, after.rotationDegrees, p),
        opacity = lerp(before.opacity, after.opacity, p),
        easing = before.easing,
        curve = before.curve,
    )
}

/** The keyframe within [KEYFRAME_SNAP_MS] of [atMs], if any. */
fun <T> List<T>.keyframeNear(atMs: Long, time: (T) -> Long): T? =
    filter { abs(time(it) - atMs) <= KEYFRAME_SNAP_MS }.minByOrNull { abs(time(it) - atMs) }

internal fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p

private fun lerpOptional(a: Float?, b: Float?, p: Float): Float? = if (a != null && b != null) lerp(a, b, p) else a ?: b

/** Same shape: every value moves; a shape change (or none ↔ some) switches at the second keyframe. */
private fun lerpMask(a: ClipMask?, b: ClipMask?, p: Float): ClipMask? {
    if (a == null || b == null || a.shape != b.shape || a.inverted != b.inverted) return if (p < 1f) a else b
    return a.copy(
        centerX = lerp(a.centerX, b.centerX, p),
        centerY = lerp(a.centerY, b.centerY, p),
        width = lerp(a.width, b.width, p),
        height = lerp(a.height, b.height, p),
        rotationDegrees = lerp(a.rotationDegrees, b.rotationDegrees, p),
        feather = lerp(a.feather, b.feather, p),
        cornerRadius = lerp(a.cornerRadius, b.cornerRadius, p),
    )
}

private fun lerpAdjust(a: Adjustments, b: Adjustments, p: Float): Adjustments = Adjustments(
    brightness = lerp(a.brightness, b.brightness, p),
    contrast = lerp(a.contrast, b.contrast, p),
    saturation = lerp(a.saturation, b.saturation, p),
    exposure = lerp(a.exposure, b.exposure, p),
    sharpen = lerp(a.sharpen, b.sharpen, p),
    highlights = lerp(a.highlights, b.highlights, p),
    shadows = lerp(a.shadows, b.shadows, p),
    temperature = lerp(a.temperature, b.temperature, p),
    tint = lerp(a.tint, b.tint, p),
    hue = lerp(a.hue, b.hue, p),
    fade = lerp(a.fade, b.fade, p),
    vignette = lerp(a.vignette, b.vignette, p),
    grain = lerp(a.grain, b.grain, p),
)
