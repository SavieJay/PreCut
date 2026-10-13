package com.savie.precut.media

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PeakAccumulatorTest {

    @Test
    fun peaksPerBucket_acrossChannels_includingPartialLastBucket() {
        // Stereo, 2 frames per bucket: frames (L,R) = (100,-200) (50,0) | (-16384,0) (0,0) | (32767, 0)
        val acc = PeakAccumulator(framesPerBucket = 2, channels = 2)
        acc.add(shortArrayOf(100, -200, 50, 0, -16384, 0))
        acc.add(shortArrayOf(0, 0, 32767, 0))
        assertArrayEquals(floatArrayOf(200 / 32768f, 0.5f, 32767 / 32768f), acc.result(), 1e-6f)
    }

    @Test
    fun respectsLength_andGrowsPastInitialCapacity() {
        val acc = PeakAccumulator(framesPerBucket = 1, channels = 1)
        val samples = ShortArray(5000) { 1000 }
        acc.add(samples, length = 3000)
        assertArrayEquals(FloatArray(3000) { 1000 / 32768f }, acc.result(), 1e-6f)
    }
}
