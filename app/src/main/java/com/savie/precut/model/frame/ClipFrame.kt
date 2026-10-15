package com.savie.precut.model.frame

import kotlinx.serialization.Serializable

/** How a clip is sized into the project frame before the user's own transform. */
@Serializable
enum class FrameFit { FIT, FILL }

/** Smallest and largest user scale, relative to [FrameFit]. */
const val MIN_CLIP_SCALE = 0.1f
const val MAX_CLIP_SCALE = 10f

/**
 * The user's move/scale/rotate of a clip in the project frame (CapCut's on-preview transform).
 * Offsets are fractions of the frame's width and height from its centre (x right, y down).
 */
@Serializable
data class ClipTransform(
    val scale: Float = 1f,
    /** Clockwise, (-180, 180]. */
    val rotationDegrees: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val isIdentity: Boolean get() = this == IDENTITY

    companion object {
        val IDENTITY = ClipTransform()
    }
}

/** A crop of the upright (mirrored and rotated) picture, as 0..1 fractions with y down. */
@Serializable
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)

        /** Smallest crop side, as a fraction of the picture. */
        const val MIN_SIDE = 0.1f
    }
}

/** Crop shapes in the Ratio row (T-12b). [ORIGINAL] is the uncropped picture's own shape. */
@Serializable
enum class CropRatio(val widthRatio: Int, val heightRatio: Int) {
    FREE(0, 0),
    ORIGINAL(0, 0),
    RATIO_9_16(9, 16),
    RATIO_16_9(16, 9),
    RATIO_1_1(1, 1),
    RATIO_4_5(4, 5),
}

@Serializable
enum class CanvasKind { COLOR, BLUR, IMAGE }

/** Built-in canvas pictures, drawn in code (T-13c). */
@Serializable
enum class CanvasPattern { DOTS, STRIPES, SAND, DUSK }

/** Blur strengths, Light to Max (T-13). */
const val MAX_CANVAS_BLUR = 4

/**
 * What fills the frame around (and under) a clip: a colour, a blur of the clip itself, or a
 * picture (an imported photo or a built-in pattern).
 */
@Serializable
data class ClipCanvas(
    val kind: CanvasKind = CanvasKind.COLOR,
    val colorArgb: Int = BLACK,
    /** 1..[MAX_CANVAS_BLUR]. */
    val blur: Int = 2,
    val imageUri: String? = null,
    /** EXIF rotation of [imageUri]. */
    val imageRotationDegrees: Int = 0,
    val pattern: CanvasPattern? = null,
) {
    /** Plain black: the same as no canvas. */
    val isDefault: Boolean
        get() = when (kind) {
            CanvasKind.COLOR -> colorArgb == BLACK
            CanvasKind.BLUR -> false
            CanvasKind.IMAGE -> imageUri == null && pattern == null
        }

    companion object {
        const val BLACK = 0xFF000000.toInt()
    }
}
