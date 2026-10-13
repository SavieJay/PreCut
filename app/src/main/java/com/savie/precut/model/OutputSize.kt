package com.savie.precut.model

data class OutputSize(val width: Int, val height: Int)

/** Default export/preview short side until export settings arrive (Phase 12). */
const val DEFAULT_SHORT_SIDE_PX = 1080

/** Output frame size for a [widthRatio]:[heightRatio] frame with the given short side; both even for encoders. */
fun outputSizeFor(widthRatio: Int, heightRatio: Int, shortSidePx: Int = DEFAULT_SHORT_SIDE_PX): OutputSize {
    fun even(value: Long) = (value / 2 * 2).toInt()
    return if (widthRatio <= heightRatio) {
        OutputSize(even(shortSidePx.toLong()), even(shortSidePx.toLong() * heightRatio / widthRatio))
    } else {
        OutputSize(even(shortSidePx.toLong() * widthRatio / heightRatio), even(shortSidePx.toLong()))
    }
}

/** Width:height of the project frame; [AspectRatio.ORIGINAL] resolves to the first clip's shape. */
fun Project.frameRatio(): Pair<Int, Int> {
    if (aspectRatio != AspectRatio.ORIGINAL) return aspectRatio.widthRatio to aspectRatio.heightRatio
    val first = clips.firstOrNull()?.takeIf { it.sourceWidth > 0 && it.sourceHeight > 0 }
        ?: return AspectRatio.RATIO_9_16.let { it.widthRatio to it.heightRatio }
    val sideways = first.sourceRotationDegrees % 180 != 0
    return if (sideways) first.sourceHeight to first.sourceWidth else first.sourceWidth to first.sourceHeight
}

/** Output frame size of the project. */
fun Project.outputSize(shortSidePx: Int = DEFAULT_SHORT_SIDE_PX): OutputSize =
    frameRatio().let { (w, h) -> outputSizeFor(w, h, shortSidePx) }
