package com.savie.precut.ui.editor.tools

import androidx.compose.ui.graphics.ColorMatrix
import com.savie.precut.media.VINTAGE_MATRIX
import com.savie.precut.model.FilterType

/**
 * Compose colour matrices (row-major 4x5, 0..255 offsets) approximating each filter for the
 * picker thumbnails. The preview and export use the real Media3 effects from EffectsFactory.
 */
fun filterPreviewMatrix(filter: FilterType): ColorMatrix? = when (filter) {
    FilterType.NONE -> null
    FilterType.BRIGHTNESS -> offset(0.12f * 255)
    FilterType.CONTRAST -> contrast(1.3f)
    FilterType.SATURATION -> ColorMatrix().apply { setToSaturation(1.45f) }
    FilterType.GRAYSCALE -> ColorMatrix().apply { setToSaturation(0f) }
    FilterType.WARM -> ColorMatrix().apply { setToScale(1.1f, 1f, 0.85f, 1f) }
    FilterType.COOL -> ColorMatrix().apply { setToScale(0.88f, 1f, 1.12f, 1f) }
    FilterType.VINTAGE -> fromColumnMajor(VINTAGE_MATRIX)
}

private fun offset(amount: Float) = ColorMatrix(
    floatArrayOf(
        1f, 0f, 0f, 0f, amount,
        0f, 1f, 0f, 0f, amount,
        0f, 0f, 1f, 0f, amount,
        0f, 0f, 0f, 1f, 0f,
    ),
)

private fun contrast(scale: Float): ColorMatrix {
    val translate = (1f - scale) * 128f
    return ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

/** Converts a 4x4 column-major RGB matrix (Media3 layout) to Compose's row-major 4x5. */
private fun fromColumnMajor(m: List<Float>) = ColorMatrix(
    floatArrayOf(
        m[0], m[4], m[8], 0f, 0f,
        m[1], m[5], m[9], 0f, 0f,
        m[2], m[6], m[10], 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)
