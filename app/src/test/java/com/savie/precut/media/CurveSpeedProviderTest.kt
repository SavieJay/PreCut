package com.savie.precut.media

import androidx.media3.common.C
import androidx.media3.common.util.SpeedProviderUtil
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.model.speed.SpeedPreset
import com.savie.precut.model.speed.SpeedSteps
import org.junit.Assert.assertEquals
import org.junit.Test

class CurveSpeedProviderTest {

    @Test
    fun timelineLength_matchesMedia3() {
        SpeedPreset.entries.forEach { preset ->
            val steps = SpeedSteps.of(SpeedCurve.preset(preset), 7_345_678L)
            val media3 = SpeedProviderUtil.getDurationAfterSpeedProviderApplied(CurveSpeedProvider(steps), steps.sourceLengthUs)
            assertEquals(preset.name, media3, steps.timelineLengthUs())
        }
    }

    @Test
    fun speedChanges_atStepBoundaries() {
        val steps = SpeedSteps.of(SpeedCurve.preset(SpeedPreset.MONTAGE), 4_000_000L)
        val provider = CurveSpeedProvider(steps)
        assertEquals(steps.boundaryUs(1), provider.getNextSpeedChangeTimeUs(0L))
        assertEquals(steps.speeds[1], provider.getSpeed(steps.boundaryUs(1)))
        assertEquals(C.TIME_UNSET, provider.getNextSpeedChangeTimeUs(steps.boundaryUs(steps.count - 1)))
    }
}
