package com.savie.precut.media

import android.media.MediaFormat

/**
 * The track's frame rate, or [default] when absent. Extractors store it as an int or a float
 * depending on the phone (Samsung's often a float), and `getInteger` on a float throws.
 */
internal fun MediaFormat.frameRate(default: Float = 30f): Float {
    if (!containsKey(MediaFormat.KEY_FRAME_RATE)) return default
    return try {
        getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
    } catch (_: ClassCastException) {
        getFloat(MediaFormat.KEY_FRAME_RATE)
    }
}
