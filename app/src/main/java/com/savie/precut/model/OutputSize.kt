package com.savie.precut.model

data class OutputSize(val width: Int, val height: Int)

/** Default export/preview short side until export settings arrive (Phase 7). */
const val DEFAULT_SHORT_SIDE_PX = 1080

/** Output frame size for [aspectRatio] with the given short side; both dimensions even for encoders. */
fun outputSizeFor(aspectRatio: AspectRatio, shortSidePx: Int = DEFAULT_SHORT_SIDE_PX): OutputSize {
    val w = aspectRatio.widthRatio
    val h = aspectRatio.heightRatio
    fun even(value: Long) = (value / 2 * 2).toInt()
    return if (w <= h) {
        OutputSize(even(shortSidePx.toLong()), even(shortSidePx.toLong() * h / w))
    } else {
        OutputSize(even(shortSidePx.toLong() * w / h), even(shortSidePx.toLong()))
    }
}
