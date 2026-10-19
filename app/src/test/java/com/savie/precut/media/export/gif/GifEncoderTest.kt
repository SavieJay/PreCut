package com.savie.precut.media.export.gif

import com.savie.precut.media.sticker.GifDecoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.random.Random

class GifEncoderTest {

    private val w = 40
    private val h = 30

    /** A frame of 65 colours, each in its own histogram bin, so the palette is exact. */
    private fun frame(squareX: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (x in squareX until squareX + 8 && y in 10 until 18) 0xFFF8F8F8.toInt()
        else (0xFF shl 24) or ((x % 8 * 32) shl 16) or ((y % 8 * 32) shl 8) or 0x40
    }

    private fun encode(frames: List<Pair<IntArray, Long>>, width: Int = w, height: Int = h, dither: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = GifEncoder(out, width, height, dither)
        frames.forEach { (pixels, ms) -> encoder.addFrame(pixels, ms) }
        encoder.finish()
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): List<Pair<IntArray, Int>> {
        val frames = mutableListOf<Pair<IntArray, Int>>()
        GifDecoder.decode(bytes) { pixels, delay -> frames += pixels.copyOf() to delay }
        return frames
    }

    @Test
    fun roundTrip_isExactWithFewColours_andKeepsTiming() {
        val source = listOf(frame(2) to 100L, frame(10) to 100L, frame(20) to 200L)
        val decoded = decode(encode(source))
        assertEquals(3, decoded.size)
        assertEquals(listOf(100, 100, 200), decoded.map { it.second })
        decoded.forEachIndexed { i, (pixels, _) -> assertArrayEquals("frame $i", source[i].first, pixels) }
    }

    @Test
    fun identicalFrames_mergeIntoOneLongerFrame() {
        val decoded = decode(encode(listOf(frame(2) to 100L, frame(2) to 100L, frame(9) to 100L)))
        assertEquals(listOf(200, 100), decoded.map { it.second })
    }

    @Test
    fun delays_stayExactOverTheWholeGif() {
        // 15 fps: 66.67 ms frames, written as hundredths without drifting.
        val frames = (0 until 15).map { frame(it) to 67L }
        val delays = decode(encode(frames)).map { it.second }
        assertEquals(15, delays.size)
        assertTrue(delays.all { it == 60 || it == 70 })
        assertTrue("total ${delays.sum()}", abs(delays.sum() - 1_000) <= 10)
    }

    @Test
    fun laterFrames_storeOnlyWhatChanged() {
        val first = encode(listOf(frame(2) to 100L)).size
        val two = encode(listOf(frame(2) to 100L, frame(3) to 100L)).size
        // The second frame is just the moved square's strip, far smaller than a whole frame.
        assertTrue("$two vs $first", two - first < first / 3)
    }

    @Test
    fun manyColours_roundTripWithinPaletteError() {
        // 256+ colours and a long LZW stream (the code table fills and clears).
        val random = Random(7)
        val width = 160
        val height = 120
        val noisy = IntArray(width * height) { (0xFF shl 24) or random.nextInt(0x1000000) }
        val decoded = decode(encode(listOf(noisy to 100L), width, height, dither = true)).single().first
        val meanError = noisy.indices.sumOf { i -> channelError(noisy[i], decoded[i]) } / noisy.size.toDouble()
        assertTrue("mean error $meanError", meanError < 60)
    }

    @Test
    fun lzw_emptyAndSingle() {
        assertTrue(GifLzw.encode(ByteArray(0), 2).isNotEmpty())
        val decoded = decode(encode(listOf(IntArray(1) { 0xFF123456.toInt() } to 50L), 1, 1)).single().first
        assertEquals(0xFF123456.toInt() and 0xFFF8F8F8.toInt(), decoded[0] and 0xFFF8F8F8.toInt())
    }

    @Test
    fun palette_neverExceedsItsLimit() {
        val random = Random(3)
        val pixels = IntArray(5_000) { (0xFF shl 24) or random.nextInt(0x1000000) }
        assertEquals(255, MedianCutPalette.build(pixels, BooleanArray(pixels.size), 255).size)
        assertEquals(1, MedianCutPalette.build(pixels, BooleanArray(pixels.size) { true }, 255).size)
    }

    @Test
    fun yuv_greyAndPrimaries() {
        val out = IntArray(1)
        YuvConverter.toArgb(byteArrayOf(128.toByte()), byteArrayOf(128.toByte()), byteArrayOf(128.toByte()), 1, 1, bt709 = false, fullRange = true, out)
        assertEquals(0xFF808080.toInt(), out[0])
        // BT.601 full-range red: Y 76, Cb 85, Cr 255.
        YuvConverter.toArgb(byteArrayOf(76), byteArrayOf(85), byteArrayOf(255.toByte()), 1, 1, bt709 = false, fullRange = true, out)
        assertTrue(channelError(0xFFFF0000.toInt(), out[0]) <= 6)
        // Video-range black and white.
        YuvConverter.toArgb(byteArrayOf(16), byteArrayOf(128.toByte()), byteArrayOf(128.toByte()), 1, 1, bt709 = true, fullRange = false, out)
        assertEquals(0xFF000000.toInt(), out[0])
        YuvConverter.toArgb(byteArrayOf(235.toByte()), byteArrayOf(128.toByte()), byteArrayOf(128.toByte()), 1, 1, bt709 = true, fullRange = false, out)
        assertTrue(channelError(0xFFFFFFFF.toInt(), out[0]) <= 3)
    }

    private fun channelError(a: Int, b: Int): Int =
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))

    @Test
    fun nearRepeats_merge_butRealChangesDont() {
        // A still re-encoded as video: a few levels of noise per frame.
        val base = frame(5)
        val noisy = IntArray(base.size) { i -> base[i] xor (i % 3) }
        assertEquals(listOf(200), decode(encode(listOf(base to 100L, noisy to 100L))).map { it.second })
        assertEquals(listOf(100, 100), decode(encode(listOf(base to 100L, frame(6) to 100L))).map { it.second })
    }

    @Test
    fun rotation_turnsClockwise() {
        // 3 × 2:  a b c / d e f
        val pixels = intArrayOf(1, 2, 3, 4, 5, 6)
        val r90 = FrameRotation.rotate(pixels, 3, 2, 90)
        assertEquals(2, r90.width)
        assertEquals(3, r90.height)
        assertArrayEquals(intArrayOf(4, 1, 5, 2, 6, 3), r90.pixels) // d a / e b / f c
        assertArrayEquals(intArrayOf(6, 5, 4, 3, 2, 1), FrameRotation.rotate(pixels, 3, 2, 180).pixels)
        assertArrayEquals(intArrayOf(3, 6, 2, 5, 1, 4), FrameRotation.rotate(pixels, 3, 2, 270).pixels) // c f / b e / a d
        assertSame(pixels, FrameRotation.rotate(pixels, 3, 2, 0).pixels)
    }
}
