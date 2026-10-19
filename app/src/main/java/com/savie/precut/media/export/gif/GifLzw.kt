package com.savie.precut.media.export.gif

/**
 * GIF's variable-length LZW (codes up to 12 bits, least significant bit first). Code sizes grow
 * and the table clears exactly as the classic GIF encoder does, so every decoder reads it back.
 */
object GifLzw {

    private const val MAX_CODE = 4_096

    fun encode(indices: ByteArray, minCodeSize: Int): ByteArray {
        val bits = BitWriter()
        val clear = 1 shl minCodeSize
        val end = clear + 1
        var codeSize = minCodeSize + 1
        var next = end + 1
        // (prefix code << 8 | next index) → code; prefixes stay below 4096.
        val table = IntArray(MAX_CODE shl 8) { -1 }

        fun emit(code: Int) {
            bits.write(code, codeSize)
            if (next >= (1 shl codeSize) && codeSize < 12) codeSize++
        }

        bits.write(clear, codeSize)
        if (indices.isEmpty()) {
            bits.write(end, codeSize)
            return bits.toByteArray()
        }
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = table[key]
            if (found >= 0) {
                prefix = found
                continue
            }
            emit(prefix)
            if (next < MAX_CODE) {
                table[key] = next++
            } else {
                // Table full: start over.
                bits.write(clear, codeSize)
                table.fill(-1)
                codeSize = minCodeSize + 1
                next = end + 1
            }
            prefix = k
        }
        emit(prefix)
        bits.write(end, codeSize)
        return bits.toByteArray()
    }

    private class BitWriter {
        private var bytes = ByteArray(4_096)
        private var length = 0
        private var buffer = 0L
        private var count = 0

        fun write(code: Int, size: Int) {
            buffer = buffer or (code.toLong() shl count)
            count += size
            while (count >= 8) {
                put((buffer and 0xFF).toInt())
                buffer = buffer ushr 8
                count -= 8
            }
        }

        fun toByteArray(): ByteArray {
            if (count > 0) put((buffer and 0xFF).toInt())
            buffer = 0
            count = 0
            return bytes.copyOf(length)
        }

        private fun put(byte: Int) {
            if (length == bytes.size) bytes = bytes.copyOf(bytes.size * 2)
            bytes[length++] = byte.toByte()
        }
    }
}
