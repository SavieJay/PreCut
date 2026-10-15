package com.savie.precut.media.reverse

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Reverses a file's sound: decodes it to 16-bit PCM in a temp file, then encodes that file from
 * its end to its start (frame order flipped) as AAC into another temp file.
 */
internal class AudioReverser(private val workDir: File) {

    /** The reversed sound of [source], or null when it has none (or an unusual PCM format). */
    suspend fun reverse(source: File): StoredTrack? {
        val pcm = File(workDir, "audio.pcm")
        val format = decodeToPcm(source, pcm) ?: return null
        return try {
            encodeBackwards(pcm, format)
        } finally {
            pcm.delete()
        }
    }

    private class PcmFormat(val sampleRate: Int, val channels: Int) {
        val frameBytes: Int get() = channels * 2
    }

    private suspend fun decodeToPcm(source: File, pcm: File): PcmFormat? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
            val track = extractor.selectTrack("audio/") ?: return null
            val input = extractor.getTrackFormat(track)
            val mime = input.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            try {
                decoder.configure(input, null, null, 0)
                decoder.start()
                var format = PcmFormat(input.getInteger(MediaFormat.KEY_SAMPLE_RATE), input.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                FileOutputStream(pcm).use { out ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (!inputDone) {
                            val index = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                            if (index >= 0) {
                                val size = extractor.readSampleData(decoder.inputBuffer(index), 0)
                                if (size < 0) {
                                    decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        val out0 = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                        if (out0 == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val changed = decoder.outputFormat
                            val encoding = if (changed.containsKey(MediaFormat.KEY_PCM_ENCODING)) changed.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                            if (encoding != AudioFormat.ENCODING_PCM_16BIT) return null
                            format = PcmFormat(changed.getInteger(MediaFormat.KEY_SAMPLE_RATE), changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                        } else if (out0 >= 0) {
                            if (info.size > 0) {
                                val buffer = decoder.outputBuffer(out0)
                                val bytes = ByteArray(info.size)
                                buffer.position(info.offset)
                                buffer.get(bytes)
                                out.write(bytes)
                            }
                            decoder.releaseOutputBuffer(out0, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
                return format
            } finally {
                decoder.release()
            }
        } finally {
            extractor.release()
        }
    }

    private suspend fun encodeBackwards(pcm: File, format: PcmFormat): StoredTrack? {
        val totalFrames = pcm.length() / format.frameBytes
        if (totalFrames == 0L) return null
        val aac = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, format.sampleRate, format.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_FRAMES * format.frameBytes)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val samplesFile = File(workDir, "audio.aac")
        val samples = mutableListOf<StoredSample>()
        var outputFormat: MediaFormat? = null
        try {
            encoder.configure(aac, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val info = MediaCodec.BufferInfo()
            var remaining = totalFrames // frames not yet fed, counted from the start of the file
            var inputDone = false
            RandomAccessFile(pcm, "r").use { input ->
                FileOutputStream(samplesFile).use { out ->
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (!inputDone) {
                            val index = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                            if (index >= 0) {
                                if (remaining == 0L) {
                                    encoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    val buffer = encoder.inputBuffer(index)
                                    val frames = minOf(remaining, (buffer.capacity() / format.frameBytes).toLong(), CHUNK_FRAMES.toLong()).toInt()
                                    val start = remaining - frames
                                    val chunk = ByteArray(frames * format.frameBytes)
                                    input.seek(start * format.frameBytes)
                                    input.readFully(chunk)
                                    buffer.clear()
                                    buffer.put(reverseFrames(chunk, format.frameBytes))
                                    val fedFrames = totalFrames - remaining
                                    encoder.queueInputBuffer(index, 0, chunk.size, fedFrames * 1_000_000L / format.sampleRate, 0)
                                    remaining = start
                                }
                            }
                        }
                        val outIndex = encoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            outputFormat = encoder.outputFormat
                        } else if (outIndex >= 0) {
                            val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (info.size > 0 && !config) {
                                val buffer = encoder.outputBuffer(outIndex)
                                val bytes = ByteArray(info.size)
                                buffer.position(info.offset)
                                buffer.get(bytes)
                                out.write(bytes)
                                samples += StoredSample(written, info.size, info.presentationTimeUs, info.flags)
                                written += info.size
                            }
                            encoder.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
            }
        } finally {
            encoder.release()
        }
        return outputFormat?.let { StoredTrack(it, samplesFile, samples) }
    }

    /** [chunk] of interleaved frames of [frameBytes] each, in reverse frame order. */
    private fun reverseFrames(chunk: ByteArray, frameBytes: Int): ByteArray {
        val frames = chunk.size / frameBytes
        val reversed = ByteArray(chunk.size)
        for (i in 0 until frames) {
            System.arraycopy(chunk, i * frameBytes, reversed, (frames - 1 - i) * frameBytes, frameBytes)
        }
        return reversed
    }

    private companion object {
        const val AUDIO_BITRATE = 128_000
        const val CHUNK_FRAMES = 4_096
    }
}
