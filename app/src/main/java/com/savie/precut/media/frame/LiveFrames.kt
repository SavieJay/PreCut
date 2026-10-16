package com.savie.precut.media.frame

import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.FrameFit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A clip's frame while a gesture or slider is still moving (preview only). The preview's canvas
 * effect for that clip reads it every frame, so the picture follows the finger without a rebuild;
 * the edit itself is committed (and the preview rebuilt) on release.
 */
@Singleton
class LiveFrames @Inject constructor() {

    /** The whole live frame state of [clipId]: values not being edited are the clip's own. */
    data class Value(val clipId: String, val fit: FrameFit, val transform: ClipTransform, val opacity: Float, val mask: ClipMask?)

    // Written on the main thread, read on the effects thread.
    @Volatile
    private var value: Value? = null

    fun set(value: Value) {
        this.value = value
    }

    fun clear() {
        value = null
    }

    /** The live frame for the clip marked by [key], if one is being edited. */
    fun current(key: String?): Value? = value?.takeIf { key != null && it.clipId == key }
}
