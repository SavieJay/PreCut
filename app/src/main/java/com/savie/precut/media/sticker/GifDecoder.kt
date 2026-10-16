package com.savie.precut.media.sticker

/** A decoded GIF's size and frame count. */
data class GifInfo(val width: Int, val height: Int, val frameCount: Int)

/**
 * Decodes an animated GIF (87a/89a) into whole composited frames (S-01 GIF stickers): local and
 * global colour tables, transparency, interlacing and the three disposal methods. Each frame is
 * handed to [onFrame] as ARGB pixels (`width * height`, reused between calls, so copy what you
 * keep) with its delay in ms. Pure Kotlin, so it runs in unit tests.
 */
object GifDecoder {
    private const val MAX_SIDE = 4_096
    private const val MAX_CODES = 4_096
    /** Browsers show frames with no (or a tiny) delay for 100 ms. */
    private const val DEFAULT_DELAY_MS = 100

    /** Whether [bytes] start like a GIF. */
    fun isGif(bytes: ByteArray): Boolean =
        bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()

    /** Null when [bytes] aren't a readable GIF; stops after [maxFrames] frames. */
    fun decode(bytes: ByteArray, maxFrames: Int = Int.MAX_VALUE, onFrame: (pixels: IntArray, delayMs: Int) -> Unit): GifInfo? {
        if (!isGif(bytes)) return null
        return try {
            Reader(bytes).decode(maxFrames, onFrame)
        } catch (_: IndexOutOfBoundsException) {
            null // Truncated file.
        }
    }

    private class Reader(private val data: ByteArray) {
        private var pos = 6

        private fun u8(): Int = data[pos++].toInt() and 0xFF
        private fun u16(): Int = u8() or (u8() shl 8)

        private fun colourTable(size: Int): IntArray = IntArray(size) { 0xFF shl 24 or (u8() shl 16) or (u8() shl 8) or u8() }

        private fun skipSubBlocks() {
            while (true) {
                val size = u8()
                if (size == 0) return
                pos += size
            }
        }

        fun decode(maxFrames: Int, onFrame: (IntArray, Int) -> Unit): GifInfo? {
            val width = u16()
            val height = u16()
            if (width !in 1..MAX_SIDE || height !in 1..MAX_SIDE) return null
            val packed = u8()
            pos += 2 // Background colour (shown as transparent, like browsers) and aspect.
            val global = if (packed and 0x80 != 0) colourTable(2 shl (packed and 7)) else null

            val canvas = IntArray(width * height)
            var saved: IntArray? = null
            var frames = 0
            // From the latest Graphic Control Extension; it applies to the next image only.
            var delayMs = 0
            var transparent = -1
            var disposal = 0

            while (pos < data.size && frames < maxFrames) {
                when (u8()) {
                    0x21 -> {
                        if (u8() == 0xF9) {
                            u8() // Block size (4).
                            val flags = u8()
                            delayMs = u16() * 10
                            val index = u8()
                            transparent = if (flags and 1 != 0) index else -1
                            disposal = (flags shr 2) and 7
                        }
                        skipSubBlocks()
                    }
                    0x2C -> {
                        val left = u16()
                        val top = u16()
                        val w = u16()
                        val h = u16()
                        val imagePacked = u8()
                        val local = if (imagePacked and 0x80 != 0) colourTable(2 shl (imagePacked and 7)) else null
                        val colours = local ?: global ?: return null
                        val indices = lzw(w * h)
                        if (disposal == 3) saved = canvas.copyOf()
                        draw(canvas, width, height, indices, colours, transparent, left, top, w, h, interlaced = imagePacked and 0x40 != 0)
                        onFrame(canvas, if (delayMs < 20) DEFAULT_DELAY_MS else delayMs)
                        frames++
                        when (disposal) {
                            2 -> clear(canvas, width, height, left, top, w, h)
                            3 -> saved?.copyInto(canvas)
                        }
                        delayMs = 0
                        transparent = -1
                        disposal = 0
                    }
                    0x3B -> break
                    else -> return null
                }
            }
            return if (frames == 0) null else GifInfo(width, height, frames)
        }

        /** The image's colour indices (missing pixels are 0). */
        private fun lzw(pixelCount: Int): ByteArray {
            val minCodeSize = u8()
            val out = ByteArray(pixelCount)
            val prefix = ShortArray(MAX_CODES)
            val suffix = ByteArray(MAX_CODES)
            val stack = ByteArray(MAX_CODES + 1)
            val clear = 1 shl minCodeSize
            val end = clear + 1
            var available = clear + 2
            var codeSize = minCodeSize + 1
            var mask = (1 shl codeSize) - 1
            var old = -1
            var first = 0
            for (code in 0 until clear) suffix[code] = code.toByte()

            var bits = 0
            var datum = 0
            var blockLeft = 0
            var written = 0
            var done = false
            var terminated = false
            while (!done) {
                if (blockLeft == 0) {
                    blockLeft = u8()
                    if (blockLeft == 0) {
                        terminated = true
                        break
                    }
                }
                datum = datum or (u8() shl bits)
                bits += 8
                blockLeft--
                while (bits >= codeSize && !done) {
                    var code = datum and mask
                    datum = datum shr codeSize
                    bits -= codeSize
                    when {
                        code == clear -> {
                            codeSize = minCodeSize + 1
                            mask = (1 shl codeSize) - 1
                            available = clear + 2
                            old = -1
                        }
                        code == end -> done = true
                        old == -1 -> {
                            if (written < pixelCount) out[written++] = suffix[code]
                            old = code
                            first = code
                        }
                        else -> {
                            val incoming = code
                            var top = 0
                            if (code >= available) {
                                stack[top++] = first.toByte()
                                code = old
                            }
                            while (code >= clear) {
                                stack[top++] = suffix[code]
                                code = prefix[code].toInt()
                            }
                            first = suffix[code].toInt() and 0xFF
                            stack[top++] = first.toByte()
                            if (available < MAX_CODES) {
                                prefix[available] = old.toShort()
                                suffix[available] = first.toByte()
                                available++
                                if (available and mask == 0 && available < MAX_CODES) {
                                    codeSize++
                                    mask = (1 shl codeSize) - 1
                                }
                            }
                            old = incoming
                            while (top > 0 && written < pixelCount) out[written++] = stack[--top]
                        }
                    }
                }
            }
            // Skip what's left of the image data, up to and including its terminator.
            if (!terminated) {
                pos += blockLeft
                skipSubBlocks()
            }
            return out
        }

        private fun draw(
            canvas: IntArray, width: Int, height: Int, indices: ByteArray, colours: IntArray,
            transparent: Int, left: Int, top: Int, w: Int, h: Int, interlaced: Boolean,
        ) {
            val rows = if (interlaced) interlacedRows(h) else IntArray(h) { it }
            for (i in 0 until h) {
                val y = top + rows[i]
                if (y !in 0 until height) continue
                for (x in 0 until w) {
                    val cx = left + x
                    if (cx !in 0 until width) continue
                    val index = indices[i * w + x].toInt() and 0xFF
                    if (index == transparent || index >= colours.size) continue
                    canvas[y * width + cx] = colours[index]
                }
            }
        }

        /** Row [i] of the stored data is image row `result[i]` (passes 0/8, 4/8, 2/4, 1/2). */
        private fun interlacedRows(h: Int): IntArray {
            val rows = IntArray(h)
            var i = 0
            for ((start, step) in listOf(0 to 8, 4 to 8, 2 to 4, 1 to 2)) {
                var y = start
                while (y < h) {
                    rows[i++] = y
                    y += step
                }
            }
            return rows
        }

        private fun clear(canvas: IntArray, width: Int, height: Int, left: Int, top: Int, w: Int, h: Int) {
            for (y in maxOf(top, 0) until minOf(top + h, height)) {
                for (x in maxOf(left, 0) until minOf(left + w, width)) canvas[y * width + x] = 0
            }
        }
    }
}
