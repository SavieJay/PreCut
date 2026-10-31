package com.savie.precut.media.cutout

import java.nio.ByteBuffer

/** One plane of a YUV 4:2:0 picture as MediaCodec hands it over (`Image.Plane`). */
class PlaneView(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)

/**
 * Samples a picture into the segmenter's input: [side] × [side] RGB values 0..1, rows top-down, of
 * the *upright* picture stretched to a square (the model sees the whole frame; the matte is
 * stretched back over it the same way). Nearest-pixel sampling straight from the decoder's planes,
 * so a 4K frame costs no more than a small one.
 */
object FrameSampler {

    /**
     * A decoded frame: its visible [width] × [height] area at [left], [top] of the planes, which
     * needs [rotationDegrees] clockwise to stand upright; BT.709 or BT.601, full or video range.
     */
    fun sampleYuv(
        y: PlaneView,
        u: PlaneView,
        v: PlaneView,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        bt709: Boolean,
        fullRange: Boolean,
        out: FloatArray,
        side: Int = SegmenterInput.SIDE,
    ) {
        require(out.size == side * side * 3) { "Wrong input size" }
        val (kr, kgb, kgr, kb) = if (bt709) BT709 else BT601
        val turn = ((rotationDegrees % 360) + 360) % 360
        for (oy in 0 until side) {
            val uy = (oy + 0.5f) / side
            for (ox in 0 until side) {
                val ux = (ox + 0.5f) / side
                // The decoded picture's point shown at upright (ux, uy), y down.
                val dx: Float
                val dy: Float
                when (turn) {
                    90 -> { dx = uy; dy = 1f - ux }
                    180 -> { dx = 1f - ux; dy = 1f - uy }
                    270 -> { dx = 1f - uy; dy = ux }
                    else -> { dx = ux; dy = uy }
                }
                val sx = left + (dx * width).toInt().coerceIn(0, width - 1)
                val sy = top + (dy * height).toInt().coerceIn(0, height - 1)
                val luma = y.buffer.get(sy * y.rowStride + sx * y.pixelStride).toInt() and 0xFF
                val cb = (u.buffer.get((sy shr 1) * u.rowStride + (sx shr 1) * u.pixelStride).toInt() and 0xFF) - 128
                val cr = (v.buffer.get((sy shr 1) * v.rowStride + (sx shr 1) * v.pixelStride).toInt() and 0xFF) - 128
                val l = if (fullRange) luma / 255f else (luma - 16) / 219f
                val pb = if (fullRange) cb / 255f else cb / 224f
                val pr = if (fullRange) cr / 255f else cr / 224f
                val i = (oy * side + ox) * 3
                out[i] = (l + kr * pr).coerceIn(0f, 1f)
                out[i + 1] = (l - kgb * pb - kgr * pr).coerceIn(0f, 1f)
                out[i + 2] = (l + kb * pb).coerceIn(0f, 1f)
            }
        }
    }

    /** An upright ARGB picture (a photo) of [width] × [height]. */
    fun sampleArgb(pixels: IntArray, width: Int, height: Int, out: FloatArray, side: Int = SegmenterInput.SIDE) {
        require(out.size == side * side * 3) { "Wrong input size" }
        for (oy in 0 until side) {
            val sy = ((oy + 0.5f) / side * height).toInt().coerceIn(0, height - 1)
            for (ox in 0 until side) {
                val sx = ((ox + 0.5f) / side * width).toInt().coerceIn(0, width - 1)
                val argb = pixels[sy * width + sx]
                val i = (oy * side + ox) * 3
                out[i] = (argb shr 16 and 0xFF) / 255f
                out[i + 1] = (argb shr 8 and 0xFF) / 255f
                out[i + 2] = (argb and 0xFF) / 255f
            }
        }
    }

    /** R from Cr; G from Cb and Cr; B from Cb. */
    private val BT601 = floatArrayOf(1.402f, 0.344136f, 0.714136f, 1.772f)
    private val BT709 = floatArrayOf(1.5748f, 0.187324f, 0.468124f, 1.8556f)
}

/** The person segmenter's input and output shape (MediaPipe's selfie segmenter: 256 × 256). */
object SegmenterInput {
    const val SIDE = 256
}
