package com.savie.precut.media.sticker

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GifDecoderTest {

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("gif/$name")) { name }.use { it.readBytes() }

    /** anim.gif: 4 frames of ffmpeg's test pattern (24×16, 250 ms, rgb8 palette), later frames drawn as changed rectangles. */
    @Test
    fun decode_matchesFfmpegFrameForFrame() {
        val gif = resource("anim.gif")
        // ffmpeg's own decoding of the same file, RGBA per frame.
        val expected = resource("anim.rgba")
        val frames = mutableListOf<IntArray>()
        val delays = mutableListOf<Int>()
        val info = GifDecoder.decode(gif) { pixels, delay ->
            frames += pixels.copyOf()
            delays += delay
        }
        assertEquals(GifInfo(24, 16, 4), info)
        assertEquals(listOf(250, 250, 250, 250), delays)
        frames.forEachIndexed { f, pixels ->
            val reference = IntArray(24 * 16) { i ->
                val o = (f * 24 * 16 + i) * 4
                fun b(k: Int) = expected[o + k].toInt() and 0xFF
                (b(3) shl 24) or (b(0) shl 16) or (b(1) shl 8) or b(2)
            }
            assertArrayEquals("frame $f", reference, pixels)
        }
    }

    @Test
    fun decode_stopsAtMaxFrames() {
        var count = 0
        val info = GifDecoder.decode(resource("anim.gif"), maxFrames = 2) { _, _ -> count++ }
        assertEquals(2, count)
        assertEquals(2, info?.frameCount)
    }

    @Test
    fun decode_handlesTransparencyAndTinyDelays() {
        // Two 2×1 frames, uncompressed LZW (a clear before every pixel): frame 2 has a transparent
        // pixel over frame 1, and both have no delay (shown for 100 ms, like browsers).
        val palette = intArrayOf(0xFF0000, 0x00FF00, 0x0000FF, 0x000000)
        val gif = TinyGif(2, 1, palette)
            .frame(intArrayOf(0, 1), transparent = null, delayCs = 0)
            .frame(intArrayOf(3, 2), transparent = 3, delayCs = 1)
            .bytes()
        val frames = mutableListOf<IntArray>()
        val delays = mutableListOf<Int>()
        GifDecoder.decode(gif) { pixels, delay -> frames += pixels.copyOf(); delays += delay }
        assertArrayEquals(intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()), frames[0])
        assertArrayEquals(intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()), frames[1])
        assertEquals(listOf(100, 100), delays)
    }

    @Test
    fun decode_rejectsOtherFilesAndTruncation() {
        assertNull(GifDecoder.decode("PNG not a gif".toByteArray()) { _, _ -> })
        val gif = resource("anim.gif")
        assertTrue(GifDecoder.isGif(gif))
        assertNull(GifDecoder.decode(gif.copyOf(40)) { _, _ -> })
    }

    /** Writes a GIF89a with a 4-colour global table and one frame per [frame] call. */
    private class TinyGif(private val width: Int, private val height: Int, private val palette: IntArray) {
        private val out = java.io.ByteArrayOutputStream()

        init {
            out.write("GIF89a".toByteArray())
            u16(width); u16(height)
            out.write(0x80 or 1) // Global table of 4 colours.
            out.write(0); out.write(0)
            palette.forEach { out.write(it shr 16 and 0xFF); out.write(it shr 8 and 0xFF); out.write(it and 0xFF) }
        }

        fun frame(indices: IntArray, transparent: Int?, delayCs: Int): TinyGif {
            out.write(0x21); out.write(0xF9); out.write(4)
            out.write(if (transparent != null) 1 else 0)
            u16(delayCs)
            out.write(transparent ?: 0)
            out.write(0)
            out.write(0x2C); u16(0); u16(0); u16(width); u16(height); out.write(0)
            val minCode = 2
            out.write(minCode)
            val clear = 1 shl minCode
            val codes = indices.flatMap { listOf(clear, it) } + (clear + 1)
            var bits = 0L
            var count = 0
            val data = mutableListOf<Int>()
            codes.forEach { code ->
                bits = bits or (code.toLong() shl count)
                count += minCode + 1
                while (count >= 8) { data += (bits and 0xFF).toInt(); bits = bits shr 8; count -= 8 }
            }
            if (count > 0) data += (bits and 0xFF).toInt()
            out.write(data.size); data.forEach { out.write(it) }
            out.write(0)
            return this
        }

        fun bytes(): ByteArray = out.apply { write(0x3B) }.toByteArray()

        private fun u16(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF) }
    }
}
