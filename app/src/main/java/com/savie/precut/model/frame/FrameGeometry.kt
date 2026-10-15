package com.savie.precut.model.frame

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a clip's (cropped) picture sits in the output frame: its centre and size as fractions of
 * the frame (y down), turned clockwise by [rotationDegrees] about the centre.
 */
data class ClipPlacement(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float,
)

/** Which guides a transform is snapped to, for guide lines and haptics. */
data class FrameSnaps(val centreX: Boolean = false, val centreY: Boolean = false, val rotation: Boolean = false) {
    val any: Boolean get() = centreX || centreY || rotation
}

object FrameGeometry {

    /** Rotations within this many degrees of a right angle snap to it. */
    const val ROTATION_SNAP_DEGREES = 5f

    /**
     * Places a [contentWidth] × [contentHeight] picture in an [outputWidth] × [outputHeight] frame:
     * fitted or filled, then the user's [transform]. Shared by the shader and the preview outline.
     */
    fun placement(
        contentWidth: Float,
        contentHeight: Float,
        outputWidth: Float,
        outputHeight: Float,
        fit: FrameFit,
        transform: ClipTransform,
    ): ClipPlacement {
        val fitScale = when (fit) {
            FrameFit.FIT -> min(outputWidth / contentWidth, outputHeight / contentHeight)
            FrameFit.FILL -> max(outputWidth / contentWidth, outputHeight / contentHeight)
        }
        val scale = fitScale * transform.scale
        return ClipPlacement(
            centerX = 0.5f + transform.offsetX,
            centerY = 0.5f + transform.offsetY,
            width = contentWidth * scale / outputWidth,
            height = contentHeight * scale / outputHeight,
            rotationDegrees = transform.rotationDegrees,
        )
    }

    /**
     * Applies a gesture (pan as frame fractions, zoom factor, extra clockwise degrees) to [start],
     * snapping the centre to the frame's centre lines within [centreSnap] and the angle to right angles.
     */
    fun applyGesture(
        start: ClipTransform,
        panX: Float,
        panY: Float,
        zoom: Float,
        rotationDelta: Float,
        centreSnap: Float,
    ): Pair<ClipTransform, FrameSnaps> {
        var x = (start.offsetX + panX).coerceIn(-MAX_OFFSET, MAX_OFFSET)
        var y = (start.offsetY + panY).coerceIn(-MAX_OFFSET, MAX_OFFSET)
        val snapX = abs(x) <= centreSnap
        val snapY = abs(y) <= centreSnap
        if (snapX) x = 0f
        if (snapY) y = 0f
        val rotation = normalizeDegrees(start.rotationDegrees + rotationDelta)
        val rightAngle = (rotation / 90f).roundToInt() * 90f
        val snapRotation = abs(rotation - rightAngle) <= ROTATION_SNAP_DEGREES
        val transform = ClipTransform(
            scale = (start.scale * zoom).coerceIn(MIN_CLIP_SCALE, MAX_CLIP_SCALE),
            rotationDegrees = if (snapRotation) normalizeDegrees(rightAngle) else rotation,
            offsetX = x,
            offsetY = y,
        )
        return transform to FrameSnaps(snapX, snapY, snapRotation)
    }

    /** Maps any angle into (-180, 180]. */
    fun normalizeDegrees(degrees: Float): Float {
        val wrapped = ((degrees % 360f) + 360f) % 360f
        return if (wrapped > 180f) wrapped - 360f else wrapped
    }

    /** Media3 `Crop` bounds (NDC, y up) for [crop]: left, right, bottom, top. */
    fun cropNdc(crop: CropRect): FloatArray =
        floatArrayOf(crop.left * 2f - 1f, crop.right * 2f - 1f, 1f - crop.bottom * 2f, 1f - crop.top * 2f)

    /** Size of the upright picture: the source as displayed, then the user's 90° turns. */
    fun uprightSize(width: Int, height: Int, sourceRotation: Int, userRotation: Int): Pair<Int, Int> =
        if ((sourceRotation + userRotation) % 180 != 0) height to width else width to height

    /** Width ÷ height the crop must keep (in pixels) for [ratio], or null when it's free. */
    fun lockedAspect(ratio: CropRatio, pictureWidth: Float, pictureHeight: Float): Float? = when (ratio) {
        CropRatio.FREE -> null
        CropRatio.ORIGINAL -> pictureWidth / pictureHeight
        else -> ratio.widthRatio.toFloat() / ratio.heightRatio
    }

    /** The largest centred crop with pixel aspect [aspect] (or the whole picture when null). */
    fun maxCentredCrop(aspect: Float?, pictureWidth: Float, pictureHeight: Float): CropRect {
        if (aspect == null) return CropRect.FULL
        val pictureAspect = pictureWidth / pictureHeight
        return if (aspect >= pictureAspect) {
            val h = pictureAspect / aspect
            CropRect(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
        } else {
            val w = aspect / pictureAspect
            CropRect((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
        }
    }

    /** [crop] after the picture turns 90° clockwise. */
    fun rotateCrop(crop: CropRect): CropRect = CropRect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right)

    /** [crop] after the picture is flipped left–right. */
    fun mirrorCrop(crop: CropRect): CropRect = CropRect(1f - crop.right, crop.top, 1f - crop.left, crop.bottom)

    /** The ratio chip after a 90° turn: portrait and landscape swap; 4:5 has no landscape twin, so it unlocks. */
    fun rotateRatio(ratio: CropRatio): CropRatio = when (ratio) {
        CropRatio.RATIO_9_16 -> CropRatio.RATIO_16_9
        CropRatio.RATIO_16_9 -> CropRatio.RATIO_9_16
        CropRatio.RATIO_4_5 -> CropRatio.FREE
        else -> ratio
    }

    /** The crop handle being dragged in crop mode. */
    enum class CropHandle { LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

    /**
     * Moves [handle] of [start] by ([dx], [dy]) picture fractions, keeping the crop inside the
     * picture, at least [CropRect.MIN_SIDE], and (for corners) at pixel aspect [aspect] when locked.
     * With a locked aspect, edge handles don't apply; the corner opposite the handle stays put.
     */
    fun dragCrop(start: CropRect, handle: CropHandle, dx: Float, dy: Float, aspect: Float?, pictureAspect: Float): CropRect {
        if (handle == CropHandle.MOVE) {
            val x = dx.coerceIn(-start.left, 1f - start.right)
            val y = dy.coerceIn(-start.top, 1f - start.bottom)
            return CropRect(start.left + x, start.top + y, start.right + x, start.bottom + y)
        }
        val movesLeft = handle in setOf(CropHandle.LEFT, CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT)
        val movesRight = handle in setOf(CropHandle.RIGHT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT)
        val movesTop = handle in setOf(CropHandle.TOP, CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT)
        val movesBottom = handle in setOf(CropHandle.BOTTOM, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT)
        if (aspect == null) {
            val min = CropRect.MIN_SIDE
            return CropRect(
                left = if (movesLeft) (start.left + dx).coerceIn(0f, start.right - min) else start.left,
                top = if (movesTop) (start.top + dy).coerceIn(0f, start.bottom - min) else start.top,
                right = if (movesRight) (start.right + dx).coerceIn(start.left + min, 1f) else start.right,
                bottom = if (movesBottom) (start.bottom + dy).coerceIn(start.top + min, 1f) else start.bottom,
            )
        }
        if (!(movesLeft || movesRight) || !(movesTop || movesBottom)) return start
        // Fraction heights per fraction width that keep the pixel aspect.
        val heightPerWidth = pictureAspect / aspect
        val anchorX = if (movesLeft) start.right else start.left
        val anchorY = if (movesTop) start.bottom else start.top
        val wantedWidth = abs((if (movesLeft) start.left + dx else start.right + dx) - anchorX)
        val wantedHeight = abs((if (movesTop) start.top + dy else start.bottom + dy) - anchorY)
        val roomWidth = if (movesLeft) anchorX else 1f - anchorX
        val roomHeight = if (movesTop) anchorY else 1f - anchorY
        val minWidth = max(CropRect.MIN_SIDE, CropRect.MIN_SIDE / heightPerWidth)
        val width = max(wantedWidth, wantedHeight / heightPerWidth)
            .coerceAtMost(min(roomWidth, roomHeight / heightPerWidth))
            .coerceAtLeast(min(minWidth, min(roomWidth, roomHeight / heightPerWidth)))
        val height = width * heightPerWidth
        val left = if (movesLeft) anchorX - width else anchorX
        val top = if (movesTop) anchorY - height else anchorY
        return CropRect(left, top, left + width, top + height)
    }

    /** How far a clip's centre may move from the frame centre, in frame fractions. */
    private const val MAX_OFFSET = 1.5f
}
