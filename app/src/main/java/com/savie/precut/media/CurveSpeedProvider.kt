package com.savie.precut.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import com.savie.precut.model.speed.SpeedSteps

/**
 * A speed curve's [steps] for Media3. Times are the clip's source time from its trim start (Media3's
 * `SpeedChangingMediaSource` subtracts the clipping start in both preview and export).
 */
@OptIn(UnstableApi::class)
class CurveSpeedProvider(private val steps: SpeedSteps) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = steps.speeds[steps.stepAt(timeUs)]

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
        val next = steps.stepAt(timeUs) + 1
        return if (next >= steps.count) C.TIME_UNSET else steps.boundaryUs(next)
    }
}
