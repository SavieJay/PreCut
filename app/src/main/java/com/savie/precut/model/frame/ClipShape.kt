package com.savie.precut.model.frame

import com.savie.precut.model.Clip

/** Width ÷ height of the clip's mirrored, rotated picture before cropping (1 when unknown). */
fun Clip.uprightAspect(): Float {
    if (sourceWidth <= 0 || sourceHeight <= 0) return 1f
    val (width, height) = FrameGeometry.uprightSize(sourceWidth, sourceHeight, sourceRotationDegrees, rotationDegrees)
    return width.toFloat() / height
}

/** Width ÷ height of what the clip shows: its upright picture after the crop. */
fun Clip.contentAspect(): Float {
    val crop = crop ?: return uprightAspect()
    return uprightAspect() * crop.width / crop.height
}
