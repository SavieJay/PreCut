package com.savie.precut.media.cutout

import com.savie.precut.model.frame.PicturePoint

/**
 * Where the person is in one segmenter picture ([width] × [height], person 1): across, the middle
 * of everything that's person; down, 40% of the way from the top of the person to the bottom, so
 * framing favours the face and shoulders. Null when too little is person to follow.
 */
object SubjectFinder {
    /** At least this share of the picture must be person (0.3%). */
    const val MIN_SHARE = 0.003f

    fun centre(width: Int, height: Int, value: (Int) -> Float): PicturePoint? {
        var count = 0
        var sumX = 0.0
        var top = -1
        var bottom = -1
        for (y in 0 until height) {
            var rowCount = 0
            for (x in 0 until width) {
                if (value(y * width + x) >= 0.5f) {
                    rowCount++
                    sumX += x
                }
            }
            if (rowCount > 0) {
                if (top < 0) top = y
                bottom = y
                count += rowCount
            }
        }
        if (count < MIN_SHARE * width * height) return null
        return PicturePoint(((sumX / count + 0.5) / width).toFloat(), (top + HEAD_BIAS * (bottom - top) + 0.5f) / height)
    }

    /** Confidence from the segmenter. */
    fun centre(confidence: FloatArray, width: Int, height: Int): PicturePoint? = centre(width, height) { confidence[it] }

    /** A matte picture (255 = person). */
    fun centre(matte: ByteArray, width: Int, height: Int): PicturePoint? = centre(width, height) { (matte[it].toInt() and 0xFF) / 255f }

    private const val HEAD_BIAS = 0.4f
}
