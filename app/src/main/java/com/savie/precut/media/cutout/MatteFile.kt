package com.savie.precut.media.cutout

import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * A background-removal matte on disk: one 8-bit picture per moment (255 = person), each deflated
 * on its own, with an index of times at the end so any moment reads without the ones before it.
 * Plain Java I/O, so it behaves the same on every phone and is tested on the JVM.
 *
 * Layout (big-endian): "PCMT", version, width, height; the frames' bytes one after another; the
 * index (count, then time µs, offset and length per frame); last, the index's offset.
 */
object MatteFile {
    internal const val MAGIC = 0x50434D54 // "PCMT"
    internal const val VERSION = 1
    private const val HEADER_BYTES = 16L
    private const val TRAILER_BYTES = 8L
    private const val INDEX_ENTRY_BYTES = 20L

    /** Writes a matte of [width] × [height] pictures to [file]; call [Writer.finish] to complete it. */
    class Writer(file: File, private val width: Int, private val height: Int) : Closeable {
        private val out = DataOutputStream(FileOutputStream(file).buffered())
        private val deflater = Deflater(Deflater.BEST_SPEED)
        private val chunk = ByteArray(width * height + 64)
        private val times = ArrayList<Long>()
        private val offsets = ArrayList<Long>()
        private val lengths = ArrayList<Int>()
        private var position = HEADER_BYTES
        private var closed = false

        init {
            require(width > 0 && height > 0) { "Empty matte" }
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(width)
            out.writeInt(height)
        }

        val frameCount: Int get() = times.size

        /** Adds the picture for [timeUs] (later than the last one; earlier ones are dropped). */
        fun add(timeUs: Long, pixels: ByteArray) {
            require(pixels.size == width * height) { "Wrong picture size" }
            if (times.isNotEmpty() && timeUs <= times.last()) return
            deflater.reset()
            deflater.setInput(pixels)
            deflater.finish()
            var length = 0
            while (!deflater.finished()) {
                val n = deflater.deflate(chunk)
                out.write(chunk, 0, n)
                length += n
            }
            times += timeUs
            offsets += position
            lengths += length
            position += length
        }

        /** Writes the index and closes the file. */
        fun finish() {
            out.writeInt(times.size)
            for (i in times.indices) {
                out.writeLong(times[i])
                out.writeLong(offsets[i])
                out.writeInt(lengths[i])
            }
            out.writeLong(position)
            close()
        }

        override fun close() {
            if (closed) return
            closed = true
            deflater.end()
            out.close()
        }
    }

    /** Reads the pictures of a finished matte; [open] returns null for a missing or damaged file. */
    class Reader private constructor(
        private val file: RandomAccessFile,
        val width: Int,
        val height: Int,
        private val times: LongArray,
        private val offsets: LongArray,
        private val lengths: IntArray,
    ) : Closeable {
        private val inflater = Inflater()
        private var packed = ByteArray(0)

        val frameCount: Int get() = times.size

        fun timeUs(index: Int): Long = times[index]

        /**
         * The picture for [timeUs]: the last one at or before it, give or take [toleranceUs] (half a
         * frame makes it the nearest one), or the first one before them all.
         */
        fun indexAt(timeUs: Long, toleranceUs: Long = 0L): Int {
            var low = 0
            var high = times.size - 1
            var found = 0
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (times[mid] <= timeUs + toleranceUs) {
                    found = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return found
        }

        /** Unpacks picture [index] into [into] (width × height bytes). */
        fun read(index: Int, into: ByteArray) {
            require(into.size == width * height) { "Wrong picture size" }
            val length = lengths[index]
            if (packed.size < length) packed = ByteArray(length)
            file.seek(offsets[index])
            file.readFully(packed, 0, length)
            inflater.reset()
            inflater.setInput(packed, 0, length)
            try {
                var filled = 0
                while (filled < into.size && !inflater.finished()) {
                    val n = inflater.inflate(into, filled, into.size - filled)
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw IOException("Truncated matte picture")
                    filled += n
                }
                if (filled < into.size) throw IOException("Short matte picture")
            } catch (e: DataFormatException) {
                throw IOException("Damaged matte picture", e)
            }
        }

        override fun close() {
            inflater.end()
            file.close()
        }

        companion object {
            fun open(path: File): Reader? {
                val file = try {
                    RandomAccessFile(path, "r")
                } catch (_: IOException) {
                    return null
                }
                return try {
                    val size = file.length()
                    if (size < HEADER_BYTES + TRAILER_BYTES + 4) throw IOException("Too short")
                    if (file.readInt() != MAGIC || file.readInt() != VERSION) throw IOException("Not a matte")
                    val width = file.readInt()
                    val height = file.readInt()
                    if (width <= 0 || height <= 0) throw IOException("Bad size")
                    file.seek(size - TRAILER_BYTES)
                    val indexAt = file.readLong()
                    if (indexAt < HEADER_BYTES || indexAt > size - TRAILER_BYTES - 4) throw IOException("Bad index")
                    file.seek(indexAt)
                    val count = file.readInt()
                    if (count <= 0 || indexAt + 4 + count * INDEX_ENTRY_BYTES != size - TRAILER_BYTES) throw IOException("Bad index")
                    val times = LongArray(count)
                    val offsets = LongArray(count)
                    val lengths = IntArray(count)
                    for (i in 0 until count) {
                        times[i] = file.readLong()
                        offsets[i] = file.readLong()
                        lengths[i] = file.readInt()
                        if (offsets[i] < HEADER_BYTES || lengths[i] <= 0 || offsets[i] + lengths[i] > indexAt) throw IOException("Bad entry")
                    }
                    Reader(file, width, height, times, offsets, lengths)
                } catch (_: IOException) {
                    file.close()
                    null
                }
            }
        }
    }
}
