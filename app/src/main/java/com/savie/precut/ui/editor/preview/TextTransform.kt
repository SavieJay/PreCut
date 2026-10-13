package com.savie.precut.ui.editor.preview

import com.savie.precut.model.MAX_TEXT_SIZE_SP
import com.savie.precut.model.MIN_TEXT_SIZE_SP
import com.savie.precut.model.TextOverlay
import kotlin.math.abs
import kotlin.math.roundToInt

/** A text's position, size and rotation while it's being transformed on the preview. */
data class TextTransform(val xNorm: Float, val yNorm: Float, val sizeSp: Float, val rotationDegrees: Float) {

    /** Which guides the transform is snapped to (for drawing guide lines and haptics). */
    data class Snaps(val centreX: Boolean, val centreY: Boolean, val rotation: Boolean)

    companion object {
        /** Rotations within this many degrees of 0/90/180/270 snap to it. */
        const val ROTATION_SNAP_DEGREES = 5f

        fun of(overlay: TextOverlay) = TextTransform(overlay.xNorm, overlay.yNorm, overlay.textSizeSp, overlay.rotationDegrees)

        /**
         * Applies a gesture (pan in frame fractions, zoom factor, rotation in degrees) to [start]
         * and snaps to the frame centre lines (within [centreSnapNorm]) and right angles.
         */
        fun applyGesture(
            start: TextTransform,
            panXNorm: Float,
            panYNorm: Float,
            zoom: Float,
            rotationDelta: Float,
            centreSnapNorm: Float,
        ): Pair<TextTransform, Snaps> {
            var x = (start.xNorm + panXNorm).coerceIn(0f, 1f)
            var y = (start.yNorm + panYNorm).coerceIn(0f, 1f)
            val snapX = abs(x - 0.5f) <= centreSnapNorm
            val snapY = abs(y - 0.5f) <= centreSnapNorm
            if (snapX) x = 0.5f
            if (snapY) y = 0.5f
            val rotation = normalizeDegrees(start.rotationDegrees + rotationDelta)
            val rightAngle = (rotation / 90f).roundToInt() * 90f
            val snapRotation = abs(rotation - rightAngle) <= ROTATION_SNAP_DEGREES
            val size = (start.sizeSp * zoom).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
            val transform = TextTransform(x, y, size, if (snapRotation) normalizeDegrees(rightAngle) else rotation)
            return transform to Snaps(snapX, snapY, snapRotation)
        }

        /** Maps any angle into (-180, 180]. */
        fun normalizeDegrees(degrees: Float): Float {
            val wrapped = ((degrees % 360f) + 360f) % 360f
            return if (wrapped > 180f) wrapped - 360f else wrapped
        }
    }
}
