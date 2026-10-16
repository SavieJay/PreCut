package com.savie.precut.model.frame

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * An affine map from an output texture coordinate (y up) to a source texture coordinate:
 * `source = col0 * u + col1 * v + t`. Pure, so the layer geometry is testable on the JVM.
 */
data class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val tx: Float, val ty: Float) {

    fun apply(u: Float, v: Float): Pair<Float, Float> = (a * u + c * v + tx) to (b * u + d * v + ty)

    /** This map, then [next] on its result. */
    fun then(next: Affine): Affine = Affine(
        a = next.a * a + next.c * b,
        b = next.b * a + next.d * b,
        c = next.a * c + next.c * d,
        d = next.b * c + next.d * d,
        tx = next.a * tx + next.c * ty + next.tx,
        ty = next.b * tx + next.d * ty + next.ty,
    )

    /** The 2×2 matrix as the shaders' `uMap` (two columns). */
    fun matrix(): FloatArray = floatArrayOf(a, b, c, d)

    fun offset(): FloatArray = floatArrayOf(tx, ty)

    companion object {
        val IDENTITY = Affine(1f, 0f, 0f, 1f, 0f, 0f)

        /** Bitmap rows run top-down; GL frames run bottom-up. */
        val FLIP_Y = Affine(1f, 0f, 0f, -1f, 0f, 1f)

        /** From a picture turned [clockwiseDegrees] (a multiple of 90) back to the unturned one. */
        fun unrotate(clockwiseDegrees: Int): Affine = when (((clockwiseDegrees % 360) + 360) % 360) {
            90 -> Affine(0f, 1f, -1f, 0f, 1f, 0f)
            180 -> Affine(-1f, 0f, 0f, -1f, 1f, 1f)
            270 -> Affine(0f, -1f, 1f, 0f, 0f, 1f)
            else -> IDENTITY
        }

        /** From a left–right flipped picture back to the original. */
        val UNMIRROR = Affine(-1f, 0f, 0f, 1f, 1f, 0f)

        /** From the cropped picture to the whole one; [crop] is in y-down fractions. */
        fun uncrop(crop: CropRect): Affine = Affine(crop.width, 0f, 0f, crop.height, crop.left, 1f - crop.bottom)
    }
}

/** How a layer's upright picture becomes the picture that gets placed: mirror, then 90° turns, then crop. */
data class LayerOrientation(val mirrored: Boolean, val rotationDegrees: Int, val crop: CropRect?) {

    /** Output coordinate of the oriented picture → coordinate in the upright picture. */
    fun map(): Affine {
        var map = crop?.let(Affine::uncrop) ?: Affine.IDENTITY
        map = map.then(Affine.unrotate(rotationDegrees))
        if (mirrored) map = map.then(Affine.UNMIRROR)
        return map
    }

    /** Pixel size of the oriented picture for an upright picture of [width] × [height]. */
    fun size(width: Int, height: Int): Pair<Int, Int> {
        val turned = ((rotationDegrees % 360) + 360) % 360 % 180 != 0
        val w = if (turned) height else width
        val h = if (turned) width else height
        val cropW = crop?.width ?: 1f
        val cropH = crop?.height ?: 1f
        return max(1, (w * cropW).roundToInt()) to max(1, (h * cropH).roundToInt())
    }
}

/**
 * The point of the upright source picture (fractions, y down) shown at frame point ([x], [y])
 * (fractions of the [outputWidth] × [outputHeight] frame, y down) by a clip placed at
 * [placement] with [orientation]; null off the picture. For picking a colour on the preview.
 */
fun sourcePointAt(
    placement: ClipPlacement,
    outputWidth: Float,
    outputHeight: Float,
    orientation: LayerOrientation,
    x: Float,
    y: Float,
): Pair<Float, Float>? {
    val dx = (x - placement.centerX) * outputWidth
    val dy = (y - placement.centerY) * outputHeight
    // Undo the clockwise turn (y down).
    val radians = Math.toRadians(placement.rotationDegrees.toDouble())
    val c = kotlin.math.cos(radians).toFloat()
    val s = kotlin.math.sin(radians).toFloat()
    val localX = dx * c + dy * s
    val localY = -dx * s + dy * c
    val u = localX / (placement.width * outputWidth) + 0.5f
    val v = localY / (placement.height * outputHeight) + 0.5f
    if (u !in 0f..1f || v !in 0f..1f) return null
    val (sx, syUp) = orientation.map().apply(u, 1f - v)
    return sx to 1f - syUp
}
