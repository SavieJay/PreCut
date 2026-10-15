package com.savie.precut.model.look

import kotlinx.serialization.Serializable

/**
 * How a clip (or a look layer) is graded: a filter at an intensity, then Adjust, HSL and Curves.
 * All defaults mean "no change".
 */
@Serializable
data class ClipLook(
    /** A [FilterCatalog] id, or `custom:<id>` for an imported `.cube` LUT; null for none. */
    val filterId: String? = null,
    /** 0..1. */
    val filterIntensity: Float = 1f,
    val adjust: Adjustments = Adjustments(),
    /** One band per [HslColor], in that order. */
    val hsl: List<HslBand> = DEFAULT_HSL,
    val curves: Curves = Curves(),
) {
    val hasFilter: Boolean get() = filterId != null && filterIntensity > 0f

    val isIdentity: Boolean
        get() = !hasFilter && adjust.isIdentity && hsl.all { it.isIdentity } && curves.isIdentity

    companion object {
        val DEFAULT_HSL: List<HslBand> = List(HslColor.entries.size) { HslBand() }
    }
}

/** CapCut's Adjust values; each is 0 for "no change". */
@Serializable
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val exposure: Float = 0f,
    val sharpen: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val hue: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
) {
    val isIdentity: Boolean get() = this == Adjustments()

    operator fun get(adjustment: Adjustment): Float = when (adjustment) {
        Adjustment.BRIGHTNESS -> brightness
        Adjustment.CONTRAST -> contrast
        Adjustment.SATURATION -> saturation
        Adjustment.EXPOSURE -> exposure
        Adjustment.SHARPEN -> sharpen
        Adjustment.HIGHLIGHTS -> highlights
        Adjustment.SHADOWS -> shadows
        Adjustment.TEMPERATURE -> temperature
        Adjustment.TINT -> tint
        Adjustment.HUE -> hue
        Adjustment.FADE -> fade
        Adjustment.VIGNETTE -> vignette
        Adjustment.GRAIN -> grain
    }

    /** A copy with [adjustment] set to [value], clamped to its range. */
    fun with(adjustment: Adjustment, value: Float): Adjustments {
        val v = value.coerceIn(adjustment.min, adjustment.max)
        return when (adjustment) {
            Adjustment.BRIGHTNESS -> copy(brightness = v)
            Adjustment.CONTRAST -> copy(contrast = v)
            Adjustment.SATURATION -> copy(saturation = v)
            Adjustment.EXPOSURE -> copy(exposure = v)
            Adjustment.SHARPEN -> copy(sharpen = v)
            Adjustment.HIGHLIGHTS -> copy(highlights = v)
            Adjustment.SHADOWS -> copy(shadows = v)
            Adjustment.TEMPERATURE -> copy(temperature = v)
            Adjustment.TINT -> copy(tint = v)
            Adjustment.HUE -> copy(hue = v)
            Adjustment.FADE -> copy(fade = v)
            Adjustment.VIGNETTE -> copy(vignette = v)
            Adjustment.GRAIN -> copy(grain = v)
        }
    }
}

/** Each Adjust slider and its range, in the design's order (T-07). */
enum class Adjustment(val min: Float, val max: Float) {
    BRIGHTNESS(-100f, 100f),
    CONTRAST(-100f, 100f),
    EXPOSURE(-100f, 100f),
    SATURATION(-100f, 100f),
    SHARPEN(0f, 100f),
    HIGHLIGHTS(-100f, 100f),
    SHADOWS(-100f, 100f),
    TEMPERATURE(-100f, 100f),
    TINT(-100f, 100f),
    HUE(-100f, 100f),
    FADE(0f, 100f),
    VIGNETTE(0f, 100f),
    GRAIN(0f, 100f),
}

/** The 8 HSL colour bands and their centre hue in degrees. */
enum class HslColor(val hueDegrees: Float) {
    RED(0f), ORANGE(30f), YELLOW(60f), GREEN(120f), CYAN(180f), BLUE(240f), PURPLE(270f), MAGENTA(310f),
}

/** Shifts for one colour band, each -100..100. Hue ±100 is ±30°. */
@Serializable
data class HslBand(val hue: Float = 0f, val saturation: Float = 0f, val lightness: Float = 0f) {
    val isIdentity: Boolean get() = hue == 0f && saturation == 0f && lightness == 0f
}

@Serializable
data class CurvePoint(val x: Float, val y: Float)

/** Tone curves: a master curve plus one per channel, each through points in 0..1. */
@Serializable
data class Curves(
    val rgb: List<CurvePoint> = IDENTITY,
    val red: List<CurvePoint> = IDENTITY,
    val green: List<CurvePoint> = IDENTITY,
    val blue: List<CurvePoint> = IDENTITY,
) {
    val isIdentity: Boolean get() = listOf(rgb, red, green, blue).all { it == IDENTITY }

    operator fun get(channel: CurveChannel): List<CurvePoint> = when (channel) {
        CurveChannel.RGB -> rgb
        CurveChannel.RED -> red
        CurveChannel.GREEN -> green
        CurveChannel.BLUE -> blue
    }

    fun with(channel: CurveChannel, points: List<CurvePoint>): Curves = when (channel) {
        CurveChannel.RGB -> copy(rgb = points)
        CurveChannel.RED -> copy(red = points)
        CurveChannel.GREEN -> copy(green = points)
        CurveChannel.BLUE -> copy(blue = points)
    }

    companion object {
        val IDENTITY = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
    }
}

enum class CurveChannel { RGB, RED, GREEN, BLUE }

/** A time-ranged filter/adjust layer that grades everything underneath (CapCut's filter/adjust track). */
@Serializable
data class LookLayer(
    val id: String,
    /** Timeline time. */
    val startMs: Long,
    val endMs: Long,
    val look: ClipLook = ClipLook(),
)
