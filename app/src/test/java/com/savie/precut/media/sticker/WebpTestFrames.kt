package com.savie.precut.media.sticker

/** A still WebP's size from its own header (`VP8X` canvas, `VP8L` or `VP8 ` bitstream), for tests. */
internal fun standaloneSize(bytes: ByteArray): Pair<Int, Int> {
    val chunks = WebpAnimation.chunks(bytes, 12)
    val first = chunks.first()
    return when (first.id) {
        "VP8X" -> (first.u24(bytes, 4) + 1) to (first.u24(bytes, 7) + 1)
        "VP8L" -> {
            val bits = first.u8(bytes, 1) or (first.u8(bytes, 2) shl 8) or (first.u8(bytes, 3) shl 16) or (first.u8(bytes, 4) shl 24)
            ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
        }
        else -> (first.u8(bytes, 6) or ((first.u8(bytes, 7) and 0x3F) shl 8)) to (first.u8(bytes, 8) or ((first.u8(bytes, 9) and 0x3F) shl 8))
    }
}
