package com.savie.precut.media.export.gif

import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Writes an animated, looping GIF89a (pure Kotlin, testable on the JVM). Each frame gets its own
 * palette ([MedianCutPalette], Floyd–Steinberg dithered when [dither]). After the first frame,
 * pixels that look the same as what's already shown are left transparent and only the changed
 * rectangle is stored, which keeps video GIFs much smaller. Identical frames merge into one longer
 * frame. Delays are kept exact over the whole GIF (GIF counts in hundredths of a second).
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    private val dither: Boolean = true,
) {
    /** What a viewer shows after the frames encoded so far (opaque ARGB). */
    private val shown = IntArray(width * height)
    private var started = false
    private var frames = 0
    /** The latest frame, held back so later identical frames can lengthen it. */
    private var pending: PendingFrame? = null
    private var totalMs = 0L
    /** The last frame as given, to spot exact repeats. */
    private var lastSource: IntArray? = null
    private var writtenCs = 0L

    private class PendingFrame(
        val left: Int, val top: Int, val w: Int, val h: Int,
        val indices: ByteArray, val palette: IntArray, val transparentIndex: Int,
        var durationMs: Long,
    )

    /** Adds a frame of [argb] pixels (row by row, [width] × [height]) shown for [durationMs]. */
    fun addFrame(argb: IntArray, durationMs: Long) {
        require(argb.size == width * height) { "Frame must be $width x $height" }
        if (!started) writeHeader()
        val previous = pending
        val last = lastSource
        if (previous != null && last != null && sameFrame(argb, last)) {
            // A repeat (a still re-encoded as video differs by a few levels): the shown frame stays
            // longer. [lastSource] keeps the frame that was encoded, so slow fades can't drift.
            previous.durationMs += durationMs
            return
        }
        lastSource = argb.copyOf()
        if (frames == 0) {
            pending = encodeRegion(argb, Rect(0, 0, width, height), useTransparency = false, durationMs)
        } else {
            val region = changedRegion(argb)
            if (region == null && previous != null) {
                // Nothing changed: the frame on screen just stays longer.
                previous.durationMs += durationMs
                return
            }
            flushPending()
            pending = encodeRegion(argb, region ?: Rect(0, 0, 1, 1), useTransparency = true, durationMs)
        }
        frames++
    }

    /** Writes the last frame and the trailer. */
    fun finish() {
        if (!started) writeHeader()
        flushPending()
        out.write(0x3B)
        out.flush()
    }

    private data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** The bounding box of pixels that differ from what's shown, or null if none do. */
    private fun changedRegion(argb: IntArray): Rect? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (changed(argb[row + x], shown[row + x])) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        return if (right < 0) null else Rect(left, top, right + 1, bottom + 1)
    }

    private fun encodeRegion(argb: IntArray, region: Rect, useTransparency: Boolean, durationMs: Long): PendingFrame {
        val w = region.right - region.left
        val h = region.bottom - region.top
        val keep = BooleanArray(w * h) // true: transparent, keep what's shown
        val colours = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (region.top + y) * width + region.left + x
                colours[y * w + x] = argb[i]
                if (useTransparency && !changed(argb[i], shown[i])) keep[y * w + x] = true
            }
        }
        val maxColours = if (useTransparency) MAX_COLOURS - 1 else MAX_COLOURS
        val palette = MedianCutPalette.build(colours, keep, maxColours)
        val transparentIndex = if (useTransparency) palette.size else -1
        val indices = palette.map(colours, w, h, keep, dither, transparentIndex)
        // Update what a viewer will show.
        for (y in 0 until h) {
            for (x in 0 until w) {
                val local = y * w + x
                val index = indices[local].toInt() and 0xFF
                if (index != transparentIndex) shown[(region.top + y) * width + region.left + x] = palette.colours[index]
            }
        }
        val table = if (useTransparency) palette.colours + 0 else palette.colours
        return PendingFrame(region.left, region.top, w, h, indices, table, transparentIndex, durationMs)
    }

    private fun flushPending() {
        val frame = pending ?: return
        pending = null
        // Exact over the whole GIF: each frame ends at the nearest hundredth to its true end.
        totalMs += frame.durationMs
        val endCs = (totalMs / 10.0).roundToLong()
        // Viewers show delays under 2/100 s as 1/10 s, so never write one that short.
        val delayCs = (endCs - writtenCs).coerceIn(MIN_DELAY_CS, 0xFFFF)
        writtenCs += delayCs
        writeFrame(frame, delayCs.toInt())
    }

    private fun writeHeader() {
        started = true
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        out.write(0x70) // no global colour table, 8-bit colour resolution
        out.write(0) // background colour index
        out.write(0) // square pixels
        // NETSCAPE2.0: loop forever.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    private fun writeFrame(frame: PendingFrame, delayCs: Int) {
        val tableBits = max(1, bitsFor(frame.palette.size))
        // Graphic control: keep the frame (disposal 1), optional transparent colour, delay.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04))
        out.write((1 shl 2) or if (frame.transparentIndex >= 0) 1 else 0)
        writeShort(delayCs)
        out.write(if (frame.transparentIndex >= 0) frame.transparentIndex else 0)
        out.write(0)
        // Image descriptor with a local colour table.
        out.write(0x2C)
        writeShort(frame.left)
        writeShort(frame.top)
        writeShort(frame.w)
        writeShort(frame.h)
        out.write(0x80 or (tableBits - 1))
        for (i in 0 until (1 shl tableBits)) {
            val c = frame.palette.getOrElse(i) { 0 }
            out.write(c shr 16 and 0xFF)
            out.write(c shr 8 and 0xFF)
            out.write(c and 0xFF)
        }
        val minCodeSize = max(2, tableBits)
        out.write(minCodeSize)
        val data = GifLzw.encode(frame.indices, minCodeSize)
        var offset = 0
        while (offset < data.size) {
            val size = minOf(255, data.size - offset)
            out.write(size)
            out.write(data, offset, size)
            offset += size
        }
        out.write(0)
    }

    private fun writeShort(value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
    }

    private companion object {
        const val MAX_COLOURS = 256
        const val MIN_DELAY_CS = 2L
        /** Colours this close (per channel) to what's shown count as unchanged. */
        const val CHANGE_TOLERANCE = 12

        /** Repeats differ from the last encoded frame by at most this per channel, everywhere. */
        const val REPEAT_TOLERANCE = 4

        fun sameFrame(a: IntArray, b: IntArray): Boolean {
            for (i in a.indices) {
                val x = a[i]
                val y = b[i]
                if (x == y) continue
                if (abs((x shr 16 and 0xFF) - (y shr 16 and 0xFF)) > REPEAT_TOLERANCE ||
                    abs((x shr 8 and 0xFF) - (y shr 8 and 0xFF)) > REPEAT_TOLERANCE ||
                    abs((x and 0xFF) - (y and 0xFF)) > REPEAT_TOLERANCE
                ) return false
            }
            return true
        }

        fun changed(a: Int, b: Int): Boolean =
            abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > CHANGE_TOLERANCE ||
                abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > CHANGE_TOLERANCE ||
                abs((a and 0xFF) - (b and 0xFF)) > CHANGE_TOLERANCE

        /** Bits for a colour table holding [count] entries (GIF tables are powers of two). */
        fun bitsFor(count: Int): Int {
            var bits = 1
            while ((1 shl bits) < count) bits++
            return bits
        }
    }
}
