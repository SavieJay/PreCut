package com.savie.precut.media.export.gif

/** Turns ARGB frames upright: Media3 stores portrait exports as landscape frames plus a rotation flag. */
object FrameRotation {

    /** A frame's pixels and size after turning. */
    class Turned(val pixels: IntArray, val width: Int, val height: Int)

    /** [pixels] ([width] × [height]) turned clockwise by [degrees] (0, 90, 180 or 270). */
    fun rotate(pixels: IntArray, width: Int, height: Int, degrees: Int): Turned {
        val turn = ((degrees % 360) + 360) % 360
        if (turn == 0) return Turned(pixels, width, height)
        val sideways = turn % 180 != 0
        val newWidth = if (sideways) height else width
        val newHeight = if (sideways) width else height
        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val (nx, ny) = when (turn) {
                    90 -> (height - 1 - y) to x
                    180 -> (width - 1 - x) to (height - 1 - y)
                    else -> y to (width - 1 - x)
                }
                out[ny * newWidth + nx] = pixels[y * width + x]
            }
        }
        return Turned(out, newWidth, newHeight)
    }
}
