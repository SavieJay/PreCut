package com.savie.precut.model.frame

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Mask shapes (P-03), in the panel's order. */
@Serializable
enum class MaskShape { LINEAR, MIRROR, CIRCLE, RECTANGLE, HEART, STAR }

/**
 * A mask on a clip's placed picture (P-03): what's outside the shape is cut away (shows the
 * canvas on the main track, or what's below on an overlay). It moves, scales and turns with the
 * clip. Positions are relative to the clip's picture, so the mask stays put as the clip moves.
 */
@Serializable
data class ClipMask(
    val shape: MaskShape,
    /** Centre as an offset from the picture's centre, in fractions of its width and height (y down). */
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    /** Size in fractions of the picture's width and height. Mirror uses [height] as its band; Linear ignores both. */
    val width: Float = 0.6f,
    val height: Float = 0.6f,
    /** Clockwise, relative to the picture. */
    val rotationDegrees: Float = 0f,
    /** Soft edge, 0..1 (1 = half the picture's short side). */
    val feather: Float = 0f,
    /** Rectangle only: 0 square corners .. 1 fully round. */
    val cornerRadius: Float = 0f,
    val inverted: Boolean = false,
) {
    /** Where a point given relative to the mask (fractions of its size, y down) lands on the picture, as offsets from its centre. */
    fun toPicture(localX: Float, localY: Float, pictureAspect: Float): Pair<Float, Float> {
        // Turn in pixel-proportional space so a turned mask keeps its shape on a non-square picture.
        val x = localX * width * pictureAspect
        val y = localY * height
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return (centerX + (x * c - y * s) / pictureAspect) to (centerY + x * s + y * c)
    }

    companion object {
        const val MIN_SIZE = 0.05f
        const val MAX_SIZE = 3f

        /** A new mask of [shape] centred on a picture of [pictureAspect] (width ÷ height), round shapes kept round. */
        fun of(shape: MaskShape, pictureAspect: Float): ClipMask {
            val side = 0.6f
            return when (shape) {
                MaskShape.LINEAR -> ClipMask(shape)
                MaskShape.MIRROR -> ClipMask(shape, width = 1f, height = 0.3f)
                MaskShape.RECTANGLE -> ClipMask(shape, width = side, height = side)
                // Square on screen: the same pixels across and down.
                else -> if (pictureAspect >= 1f) {
                    ClipMask(shape, width = side / pictureAspect, height = side)
                } else {
                    ClipMask(shape, width = side, height = side * pictureAspect)
                }
            }
        }
    }
}

/** Mask edits from gestures on the preview; all pure. */
object MaskGeometry {

    /** [mask] moved by a pan of ([dx], [dy]) picture fractions. */
    fun moved(mask: ClipMask, dx: Float, dy: Float): ClipMask = mask.copy(
        centerX = (mask.centerX + dx).coerceIn(-1f, 1f),
        centerY = (mask.centerY + dy).coerceIn(-1f, 1f),
    )

    /** [mask] scaled by [zoom] (both sides) and turned by [degrees] clockwise. */
    fun scaledAndTurned(mask: ClipMask, zoom: Float, degrees: Float): ClipMask = mask.copy(
        width = (mask.width * zoom).coerceIn(ClipMask.MIN_SIZE, ClipMask.MAX_SIZE),
        height = (mask.height * zoom).coerceIn(ClipMask.MIN_SIZE, ClipMask.MAX_SIZE),
        rotationDegrees = FrameGeometry.normalizeDegrees(mask.rotationDegrees + degrees),
    )

    /** [mask] with its sides set separately (the corner handle), in picture fractions. */
    fun resized(mask: ClipMask, width: Float, height: Float): ClipMask = mask.copy(
        width = abs(width).coerceIn(ClipMask.MIN_SIZE, ClipMask.MAX_SIZE),
        height = abs(height).coerceIn(ClipMask.MIN_SIZE, ClipMask.MAX_SIZE),
    )

    fun feathered(mask: ClipMask, feather: Float): ClipMask = mask.copy(feather = feather.coerceIn(0f, 1f))
}

/**
 * Chroma key (P-04): pixels close to [colorArgb] turn transparent. [intensity] widens the range
 * of colours removed; [shadow] softens the edge so shadows and fine detail fade out gradually.
 */
@Serializable
data class ChromaKey(
    val colorArgb: Int,
    val intensity: Float = 0.5f,
    val shadow: Float = 0.2f,
) {
    /** The key's distance threshold and soft-edge width in CbCr space. */
    fun thresholds(): Pair<Float, Float> = (0.02f + intensity.coerceIn(0f, 1f) * 0.32f) to (0.005f + shadow.coerceIn(0f, 1f) * 0.2f)

    companion object {
        /** Studio green, CapCut's starting point. */
        const val DEFAULT_COLOR = 0xFF19C24A.toInt()
    }
}
