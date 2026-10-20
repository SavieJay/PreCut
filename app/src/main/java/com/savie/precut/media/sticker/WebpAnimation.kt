package com.savie.precut.media.sticker

/**
 * Plays animated WebP stickers frame by frame. Android can't decode a chosen frame of one
 * (`ImageDecoder` gives the first, `AnimatedImageDrawable` plays on its own clock), but each
 * frame's picture is an ordinary still WebP bitstream: this reads the animation container (RIFF
 * `VP8X`, `ANIM`, `ANMF` chunks), wraps every frame's `VP8 `/`VP8L` data (with its `ALPH`) as a
 * standalone WebP for [decode]'s `decodeFrame` (Android's `BitmapFactory` in the app), and
 * composites the frames on the canvas with their offsets, blending and disposal, as the WebP
 * container spec describes. Pure Kotlin, so it runs in unit tests.
 */
object WebpAnimation {
    private const val MAX_SIDE = 4_096
    /** As for GIFs, browsers show frames with no (or a tiny) duration for 100 ms. */
    private const val DEFAULT_DURATION_MS = 100
    private const val FLAG_ANIMATION = 0x02
    private const val FLAG_ALPHA = 0x10

    /** Whether [bytes] are a WebP with the animation flag set. */
    fun isAnimated(bytes: ByteArray): Boolean = try {
        isWebp(bytes) && chunks(bytes, 12).firstOrNull()?.let { it.id == "VP8X" && it.u8(bytes, 0) and FLAG_ANIMATION != 0 } == true
    } catch (_: IndexOutOfBoundsException) {
        false
    }

    /** The canvas size of an animated WebP; null for anything else. */
    fun canvasSize(bytes: ByteArray): Pair<Int, Int>? {
        if (!isAnimated(bytes)) return null
        val header = chunks(bytes, 12).first()
        return (header.u24(bytes, 4) + 1) to (header.u24(bytes, 7) + 1)
    }

    /**
     * Decodes up to [maxFrames] whole frames, handing each to [onFrame] as canvas-sized ARGB
     * (straight alpha; reused between calls, so copy what you keep) with its duration in ms.
     * [decodeFrame] turns one standalone WebP into ARGB pixels (its own size) or null. Null when
     * [bytes] aren't a readable animated WebP.
     */
    fun decode(
        bytes: ByteArray,
        maxFrames: Int = Int.MAX_VALUE,
        decodeFrame: (ByteArray) -> IntArray?,
        onFrame: (pixels: IntArray, durationMs: Int) -> Unit,
    ): AnimationInfo? {
        if (!isAnimated(bytes)) return null
        return try {
            decodeAnimated(bytes, maxFrames, decodeFrame, onFrame)
        } catch (_: IndexOutOfBoundsException) {
            null // Truncated file.
        }
    }

    private fun decodeAnimated(
        bytes: ByteArray,
        maxFrames: Int,
        decodeFrame: (ByteArray) -> IntArray?,
        onFrame: (IntArray, Int) -> Unit,
    ): AnimationInfo? {
        val all = chunks(bytes, 12)
        val header = all.first()
        val width = header.u24(bytes, 4) + 1
        val height = header.u24(bytes, 7) + 1
        if (width > MAX_SIDE || height > MAX_SIDE) return null
        val canvas = IntArray(width * height)
        var disposeRect: IntArray? = null
        var count = 0
        for (chunk in all) {
            if (chunk.id != "ANMF") continue
            if (count >= maxFrames) break
            disposeRect?.let { (x, y, w, h) -> clear(canvas, width, x, y, w, h) }
            val x = chunk.u24(bytes, 0) * 2
            val y = chunk.u24(bytes, 3) * 2
            val w = chunk.u24(bytes, 6) + 1
            val h = chunk.u24(bytes, 9) + 1
            val duration = chunk.u24(bytes, 12).let { if (it <= 10) DEFAULT_DURATION_MS else it }
            val flags = chunk.u8(bytes, 15)
            val blend = flags and 0x02 == 0
            val pixels = frameBytes(bytes, chunk, w, h)?.let(decodeFrame)?.takeIf { it.size == w * h } ?: return null
            draw(canvas, width, height, pixels, x, y, w, h, blend)
            disposeRect = if (flags and 0x01 != 0) intArrayOf(x, y, w, h) else null
            onFrame(canvas, duration)
            count++
        }
        return if (count == 0) null else AnimationInfo(width, height, count)
    }

    /** A frame's picture as a standalone WebP: `VP8L` alone, `VP8 ` alone, or `VP8 ` with its `ALPH` under a `VP8X`. */
    internal fun frameBytes(bytes: ByteArray, frame: Chunk, width: Int, height: Int): ByteArray? {
        val inner = chunks(bytes, frame.start + 16, frame.end)
        val image = inner.firstOrNull { it.id == "VP8 " || it.id == "VP8L" } ?: return null
        val alpha = inner.firstOrNull { it.id == "ALPH" }?.takeIf { image.id == "VP8 " }
        val parts = mutableListOf<ByteArray>()
        if (alpha != null) {
            val header = ByteArray(10)
            header[0] = FLAG_ALPHA.toByte()
            putU24(header, 4, width - 1)
            putU24(header, 7, height - 1)
            parts += chunkBytes("VP8X", header)
            parts += alpha.raw(bytes)
        }
        parts += image.raw(bytes)
        val body = parts.fold(ByteArray(0)) { acc, part -> acc + part }
        val out = ByteArray(12 + body.size)
        "RIFF".encodeToByteArray().copyInto(out, 0)
        putU32(out, 4, 4 + body.size)
        "WEBP".encodeToByteArray().copyInto(out, 8)
        body.copyInto(out, 12)
        return out
    }

    /** A chunk: its id, and its payload's place in the file ([start] until [end]). */
    internal class Chunk(val id: String, val start: Int, val end: Int) {
        fun u8(bytes: ByteArray, offset: Int): Int = bytes[start + offset].toInt() and 0xFF
        fun u24(bytes: ByteArray, offset: Int): Int = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8) or (u8(bytes, offset + 2) shl 16)

        /** The whole chunk as in the file: header, payload and padding. */
        fun raw(bytes: ByteArray): ByteArray = chunkBytes(id, bytes.copyOfRange(start, end))
    }

    internal fun chunks(bytes: ByteArray, from: Int, until: Int = bytes.size): List<Chunk> {
        val out = mutableListOf<Chunk>()
        var pos = from
        while (pos + 8 <= until) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = u32(bytes, pos + 4)
            val start = pos + 8
            val end = start + size
            if (size < 0 || end > until) throw IndexOutOfBoundsException("Chunk $id runs past its container")
            out += Chunk(id, start, end)
            pos = end + (size and 1)
        }
        return out
    }

    private fun isWebp(bytes: ByteArray): Boolean =
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"

    /** Draws a frame: alpha-blended over the canvas, or replacing it. */
    private fun draw(canvas: IntArray, width: Int, height: Int, pixels: IntArray, x: Int, y: Int, w: Int, h: Int, blend: Boolean) {
        for (row in 0 until h) {
            val cy = y + row
            if (cy !in 0 until height) continue
            for (col in 0 until w) {
                val cx = x + col
                if (cx !in 0 until width) continue
                val i = cy * width + cx
                val src = pixels[row * w + col]
                canvas[i] = if (blend) over(src, canvas[i]) else src
            }
        }
    }

    private fun clear(canvas: IntArray, width: Int, x: Int, y: Int, w: Int, h: Int) {
        val height = canvas.size / width
        for (cy in maxOf(0, y) until minOf(height, y + h)) canvas.fill(0, cy * width + maxOf(0, x), cy * width + minOf(width, x + w))
    }

    /** Straight-alpha source-over. */
    internal fun over(src: Int, dst: Int): Int {
        val sa = src ushr 24
        if (sa == 255) return src
        if (sa == 0) return dst
        val da = dst ushr 24
        val outA = sa + da * (255 - sa) / 255
        if (outA == 0) return 0
        fun channel(shift: Int): Int {
            val s = (src shr shift) and 0xFF
            val d = (dst shr shift) and 0xFF
            return ((s * sa + d * da * (255 - sa) / 255) / outA).coerceIn(0, 255)
        }
        return (outA shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun chunkBytes(id: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size + (payload.size and 1))
        id.encodeToByteArray().copyInto(out, 0)
        putU32(out, 4, payload.size)
        payload.copyInto(out, 8)
        return out
    }

    private fun u32(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or ((bytes[at + 3].toInt() and 0xFF) shl 24)

    private fun putU24(out: ByteArray, at: Int, value: Int) {
        out[at] = value.toByte()
        out[at + 1] = (value shr 8).toByte()
        out[at + 2] = (value shr 16).toByte()
    }

    private fun putU32(out: ByteArray, at: Int, value: Int) {
        putU24(out, at, value)
        out[at + 3] = (value shr 24).toByte()
    }
}
