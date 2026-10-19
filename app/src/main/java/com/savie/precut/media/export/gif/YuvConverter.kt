package com.savie.precut.media.export.gif

/**
 * YUV 4:2:0 (tightly packed planes) to opaque ARGB, with BT.601 or BT.709 coefficients and video
 * (16–235) or full range, in fixed point.
 */
object YuvConverter {

    fun toArgb(y: ByteArray, u: ByteArray, v: ByteArray, width: Int, height: Int, bt709: Boolean, fullRange: Boolean, out: IntArray) {
        val chromaWidth = (width + 1) / 2
        // R = Y + a·Cr, G = Y − b·Cb − c·Cr, B = Y + d·Cb, in 1/1024ths.
        val a = if (bt709) 1613 else 1436
        val b = if (bt709) 192 else 352
        val c = if (bt709) 479 else 731
        val d = if (bt709) 1900 else 1815
        for (row in 0 until height) {
            val chromaRow = (row shr 1) * chromaWidth
            for (col in 0 until width) {
                val luma = y[row * width + col].toInt() and 0xFF
                val cb = (u[chromaRow + (col shr 1)].toInt() and 0xFF) - 128
                val cr = (v[chromaRow + (col shr 1)].toInt() and 0xFF) - 128
                // Video range stretches luma 16–235 and chroma ±112 to full.
                val l = if (fullRange) luma shl 10 else (luma - 16) * 1192
                val cbs = if (fullRange) cb else cb * 1144 shr 10
                val crs = if (fullRange) cr else cr * 1144 shr 10
                val r = (l + a * crs) shr 10
                val g = (l - b * cbs - c * crs) shr 10
                val bl = (l + d * cbs) shr 10
                out[row * width + col] = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or bl.coerceIn(0, 255)
            }
        }
    }
}
