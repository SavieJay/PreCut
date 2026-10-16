package com.savie.precut.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import com.savie.precut.model.keyframe.ClipKeyframe
import com.savie.precut.model.keyframe.valuesAt

/** Volume from keyframes (times into the clip, timeline ms), for the clip's own sound. */
@OptIn(UnstableApi::class)
class KeyframeGainProvider(
    private val keyframes: List<ClipKeyframe>,
    /** Where the item starts in its clip (timeline ms). */
    private val offsetMs: Long = 0L,
) : GainProcessor.GainProvider {

    override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float =
        keyframes.valuesAt(offsetMs + samplePosition * 1_000 / sampleRate)?.volume ?: 1f

    // Volume can change anywhere between keyframes, so no stretch is known to be unity.
    override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long = C.TIME_UNSET
}
