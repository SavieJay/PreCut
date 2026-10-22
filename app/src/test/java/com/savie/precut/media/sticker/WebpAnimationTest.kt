package com.savie.precut.media.sticker

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebpAnimationTest {

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("webp/$name")) { "missing $name" }.use { it.readBytes() }

    // ---- Real files (ffmpeg's libwebp_anim): container, durations and standalone frames ----

    private fun decodeReal(bytes: ByteArray): Triple<AnimationInfo?, List<Int>, List<ByteArray>> {
        val durations = mutableListOf<Int>()
        val frames = mutableListOf<ByteArray>()
        val info = WebpAnimation.decode(
            bytes,
            decodeFrame = { standalone ->
                frames += standalone
                val (w, h) = standaloneSize(standalone)
                IntArray(w * h) { 0xFF336699.toInt() }
            },
        ) { _, duration -> durations += duration }
        return Triple(info, durations, frames)
    }

    @Test
    fun `lossy frames with alpha come out as VP8X + ALPH + VP8`() {
        val (info, durations, frames) = decodeReal(fixture("lossy_alpha.webp"))
        assertEquals(AnimationInfo(40, 30, 4), info)
        assertEquals(listOf(200, 400, 200, 200), durations)
        for (frame in frames) {
            assertEquals("RIFF size", frame.size - 8, frame[4].toInt() and 0xFF or ((frame[5].toInt() and 0xFF) shl 8))
            assertEquals(listOf("VP8X", "ALPH", "VP8 "), WebpAnimation.chunks(frame, 12).map { it.id })
            assertEquals(0x10, WebpAnimation.chunks(frame, 12).first().u8(frame, 0))
        }
    }

    @Test
    fun `lossless frames come out as a lone VP8L`() {
        val (info, durations, frames) = decodeReal(fixture("lossless.webp"))
        assertEquals(AnimationInfo(40, 30, 4), info)
        assertEquals(listOf(200, 400, 200, 200), durations)
        frames.forEach { assertEquals(listOf("VP8L"), WebpAnimation.chunks(it, 12).map { c -> c.id }) }
        assertEquals(40 to 30, WebpAnimation.canvasSize(fixture("lossless.webp")))
    }

    @Test
    fun `only animated WebPs are taken`() {
        assertTrue(WebpAnimation.isAnimated(fixture("lossless.webp")))
        // The first 30 bytes (RIFF header and VP8X) are enough, as the library listing reads.
        assertTrue(WebpAnimation.isAnimated(fixture("lossless.webp").copyOf(30)))
        assertFalse(WebpAnimation.isAnimated("GIF89a".encodeToByteArray() + ByteArray(30)))
        assertFalse(WebpAnimation.isAnimated(container(width = 1, height = 1, animated = false, frames = emptyList())))
        assertNull(WebpAnimation.decode(fixture("lossless.webp").copyOf(200), decodeFrame = { null }) { _, _ -> })
    }

    // ---- Compositing, on a hand-built container whose "frames" a fake decoder fills with one colour ----

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val halfBlue = 0x800000FF.toInt()

    /** A fake frame: a `VP8L` chunk holding the frame's size and colour (the fake decoder's input). */
    private class FakeFrame(val x: Int, val y: Int, val w: Int, val h: Int, val colour: Int, val blend: Boolean, val dispose: Boolean, val ms: Int = 100)

    private fun fakeDecode(standalone: ByteArray): IntArray {
        val chunk = WebpAnimation.chunks(standalone, 12).single()
        val w = chunk.u8(standalone, 0)
        val h = chunk.u8(standalone, 1)
        val colour = (chunk.u24(standalone, 2)) or (chunk.u8(standalone, 5) shl 24)
        return IntArray(w * h) { colour }
    }

    private fun chunk(id: String, payload: ByteArray): ByteArray {
        val size = payload.size
        val header = id.encodeToByteArray() + byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte())
        return header + payload + if (size % 2 == 1) byteArrayOf(0) else byteArrayOf()
    }

    private fun u24(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte())

    private fun container(width: Int, height: Int, animated: Boolean = true, frames: List<FakeFrame>): ByteArray {
        var body = chunk("VP8X", byteArrayOf(if (animated) 0x02 else 0, 0, 0, 0) + u24(width - 1) + u24(height - 1))
        body += chunk("ANIM", ByteArray(6))
        for (f in frames) {
            val fake = byteArrayOf(f.w.toByte(), f.h.toByte()) + u24(f.colour and 0xFFFFFF) + byteArrayOf((f.colour ushr 24).toByte())
            val flags = (if (f.blend) 0 else 2) or (if (f.dispose) 1 else 0)
            body += chunk("ANMF", u24(f.x / 2) + u24(f.y / 2) + u24(f.w - 1) + u24(f.h - 1) + u24(f.ms) + byteArrayOf(flags.toByte()) + chunk("VP8L", fake))
        }
        val size = 4 + body.size
        return "RIFF".encodeToByteArray() + byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte()) +
            "WEBP".encodeToByteArray() + body
    }

    private fun composite(bytes: ByteArray): List<IntArray> {
        val frames = mutableListOf<IntArray>()
        assertNotNull(WebpAnimation.decode(bytes, decodeFrame = ::fakeDecode) { pixels, _ -> frames += pixels.copyOf() })
        return frames
    }

    @Test
    fun `frames land at their offsets, blend or replace, and dispose to transparent`() {
        val frames = composite(
            container(
                width = 4, height = 1,
                frames = listOf(
                    FakeFrame(0, 0, 4, 1, red, blend = false, dispose = false),
                    FakeFrame(2, 0, 2, 1, halfBlue, blend = true, dispose = true),
                    FakeFrame(0, 0, 1, 1, green, blend = false, dispose = false),
                    FakeFrame(2, 0, 1, 1, halfBlue, blend = false, dispose = false),
                ),
            ),
        )
        val blended = WebpAnimation.over(halfBlue, red)
        assertArrayEquals(intArrayOf(red, red, red, red), frames[0])
        assertArrayEquals(intArrayOf(red, red, blended, blended), frames[1])
        // Frame 2 was disposed: its area is clear again before frame 3.
        assertArrayEquals(intArrayOf(green, red, 0, 0), frames[2])
        // Without blending, a frame's own alpha replaces what was there.
        assertArrayEquals(intArrayOf(green, red, halfBlue, 0), frames[3])
    }

    @Test
    fun `source over keeps straight alpha`() {
        assertEquals(red, WebpAnimation.over(red, green))
        assertEquals(green, WebpAnimation.over(0, green))
        val mixed = WebpAnimation.over(halfBlue, red)
        assertEquals(0xFF, mixed ushr 24)
        assertTrue((mixed shr 16 and 0xFF) in 125..129 && (mixed and 0xFF) in 126..130)
        // Over nothing, the colour stays and only the alpha shows it's half.
        assertEquals(halfBlue, WebpAnimation.over(halfBlue, 0))
    }

    @Test
    fun `tiny durations play at 100 ms, like browsers`() {
        val durations = mutableListOf<Int>()
        WebpAnimation.decode(
            container(2, 2, frames = listOf(FakeFrame(0, 0, 2, 2, red, false, false, ms = 0), FakeFrame(0, 0, 2, 2, green, false, false, ms = 50))),
            decodeFrame = ::fakeDecode,
        ) { _, ms -> durations += ms }
        assertEquals(listOf(100, 50), durations)
    }
}
