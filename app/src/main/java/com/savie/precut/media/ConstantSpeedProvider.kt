package com.savie.precut.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi

/** The same playback speed for the whole item. */
@OptIn(UnstableApi::class)
class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}
