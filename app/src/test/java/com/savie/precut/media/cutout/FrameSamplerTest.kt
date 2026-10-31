package com.savie.precut.media.cutout

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class FrameSamplerTest {

    /** A 4 × 2 frame whose luma names each pixel (20, 30, … row by row) and neutral chroma. */
    private val lumaValues = IntArray(8) { 20 + it * 10 }
    private fun plane(values: IntArray, rowStride: Int, pixelStride: Int = 1) =
        PlaneView(ByteBuffer.wrap(ByteArray(values.size) { values[it].toByte() }), rowStride, pixelStride)
    private val y = plane(lumaValues, rowStride = 4)
    private val neutral = plane(intArrayOf(128, 128), rowStride = 2)

    /** The luma (full range) the sampler picked at output pixel ([x], [y]) of a [side]-square input. */
    private fun lumaAt(out: FloatArray, side: Int, x: Int, y: Int) = Math.round(out[(y * side + x) * 3] * 255f)

    private fun sample(rotation: Int, side: Int = 4): FloatArray = FloatArray(side * side * 3).also {
        FrameSampler.sampleYuv(y, neutral, neutral, 0, 0, 4, 2, rotation, bt709 = false, fullRange = true, out = it, side = side)
    }

    @Test
    fun upright_isStretchedToTheSquare() {
        val out = sample(rotation = 0)
        assertEquals(20, lumaAt(out, 4, 0, 0))
        assertEquals(50, lumaAt(out, 4, 3, 0))
        // Two rows of the frame fill four rows of the square.
        assertEquals(20, lumaAt(out, 4, 0, 1))
        assertEquals(60, lumaAt(out, 4, 0, 2))
        assertEquals(90, lumaAt(out, 4, 3, 3))
        // Grey stays grey.
        assertEquals(out[0], out[1], 1e-6f)
        assertEquals(out[0], out[2], 1e-6f)
    }

    @Test
    fun rotatedFrames_standUpright() {
        // A clockwise quarter turn: the frame's bottom-left is the upright top-left.
        val cw = sample(rotation = 90)
        assertEquals(60, lumaAt(cw, 4, 0, 0))
        assertEquals(20, lumaAt(cw, 4, 3, 0))
        assertEquals(90, lumaAt(cw, 4, 0, 3))
        assertEquals(50, lumaAt(cw, 4, 3, 3))
        val half = sample(rotation = 180)
        assertEquals(90, lumaAt(half, 4, 0, 0))
        assertEquals(20, lumaAt(half, 4, 3, 3))
        val ccw = sample(rotation = 270)
        assertEquals(50, lumaAt(ccw, 4, 0, 0))
        assertEquals(20, lumaAt(ccw, 4, 0, 3))
        assertEquals(sample(rotation = 270).toList(), sample(rotation = -90).toList())
    }

    @Test
    fun cropOffset_samplesTheVisibleArea() {
        // The visible 2 × 2 area starts at column 2 of the planes.
        val out = FloatArray(2 * 2 * 3)
        FrameSampler.sampleYuv(y, neutral, neutral, left = 2, top = 0, width = 2, height = 2, rotationDegrees = 0, bt709 = false, fullRange = true, out = out, side = 2)
        assertEquals(40, lumaAt(out, 2, 0, 0))
        assertEquals(90, lumaAt(out, 2, 1, 1))
    }

    @Test
    fun colour_followsTheStandardAndRange() {
        val luma = plane(intArrayOf(128, 128, 128, 128), rowStride = 2)
        // Interleaved chroma (pixel stride 2), as many decoders hand it over: Cb then Cr.
        val chroma = ByteBuffer.wrap(byteArrayOf(128.toByte(), 228.toByte()))
        val u = PlaneView(chroma, rowStride = 2, pixelStride = 2)
        val v = PlaneView(ByteBuffer.wrap(byteArrayOf(228.toByte(), 128.toByte())), rowStride = 2, pixelStride = 2)
        val bt601 = FloatArray(3).also { FrameSampler.sampleYuv(luma, u, v, 0, 0, 2, 2, 0, bt709 = false, fullRange = true, out = it, side = 1) }
        val bt709 = FloatArray(3).also { FrameSampler.sampleYuv(luma, u, v, 0, 0, 2, 2, 0, bt709 = true, fullRange = true, out = it, side = 1) }
        // Strong Cr: red clips, green drops (less in BT.709), blue stays at the luma.
        assertEquals(1f, bt601[0], 1e-6f)
        assertEquals(0.222f, bt601[1], 0.005f)
        assertEquals(0.502f, bt601[2], 0.005f)
        assertEquals(0.318f, bt709[1], 0.005f)
        // Video range: 16 is black and 235 white.
        val dark = FloatArray(3).also { FrameSampler.sampleYuv(plane(intArrayOf(16), 1), neutral, neutral, 0, 0, 1, 1, 0, bt709 = false, fullRange = false, out = it, side = 1) }
        val light = FloatArray(3).also { FrameSampler.sampleYuv(plane(intArrayOf(235), 1), neutral, neutral, 0, 0, 1, 1, 0, bt709 = false, fullRange = false, out = it, side = 1) }
        assertEquals(0f, dark[0], 1e-6f)
        assertEquals(1f, light[0], 1e-6f)
    }

    @Test
    fun photo_isStretchedToTheSquare() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val out = FloatArray(2 * 2 * 3)
        FrameSampler.sampleArgb(intArrayOf(red, blue), width = 2, height = 1, out = out, side = 2)
        assertEquals(listOf(1f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 1f), out.toList())
    }
}
