package com.savie.precut.media.reverse

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/** Thrown when this phone's codecs can't do a reverse (e.g. no all-keyframe encoding). */
class ReverseUnsupportedException(message: String) : Exception(message)

/** One encoded sample kept in a temp file until the muxer starts. */
data class StoredSample(val offset: Long, val size: Int, val presentationTimeUs: Long, val flags: Int)

/** Encoded samples written to [file] in order, with their [format] (for the muxer). */
class StoredTrack(val format: MediaFormat, val file: File, val samples: List<StoredSample>) {

    /** Hands each sample to [write], read back from the temp file. */
    fun forEach(write: (ByteBuffer, MediaCodec.BufferInfo) -> Unit) {
        RandomAccessFile(file, "r").use { input ->
            val info = MediaCodec.BufferInfo()
            for (sample in samples) {
                val bytes = ByteArray(sample.size)
                input.seek(sample.offset)
                input.readFully(bytes)
                info.set(0, sample.size, sample.presentationTimeUs, sample.flags)
                write(ByteBuffer.wrap(bytes), info)
            }
        }
    }
}

/** The index of the first track whose MIME type starts with [prefix], selected; or null. */
internal fun MediaExtractor.selectTrack(prefix: String): Int? {
    for (i in 0 until trackCount) {
        if (getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true) {
            selectTrack(i)
            return i
        }
    }
    return null
}

internal fun MediaCodec.inputBuffer(index: Int): ByteBuffer =
    getInputBuffer(index) ?: throw IllegalStateException("No input buffer $index")

internal fun MediaCodec.outputBuffer(index: Int): ByteBuffer =
    getOutputBuffer(index) ?: throw IllegalStateException("No output buffer $index")

/** Microseconds a codec call may wait before the loop checks something else. */
internal const val CODEC_TIMEOUT_US = 10_000L
