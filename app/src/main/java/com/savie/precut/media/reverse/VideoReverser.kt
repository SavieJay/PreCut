package com.savie.precut.media.reverse

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * Reverses the video of an all-keyframe file: each frame is read from the last to the first and
 * decoded straight onto an encoder's input surface with a new, rising timestamp. The result is
 * muxed with the already reversed [audio].
 */
internal class VideoReverser {

    suspend fun reverse(source: File, audio: StoredTrack?, target: File, onProgress: (Float) -> Unit) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
            val track = extractor.selectTrack("video/") ?: throw ReverseUnsupportedException("No video track")
            val input = extractor.getTrackFormat(track)
            val times = sampleTimes(extractor)
            if (times.isEmpty()) throw ReverseUnsupportedException("No video frames")
            transcode(extractor, input, times, audio, target, onProgress)
        } finally {
            extractor.release()
        }
    }

    /** Every frame's time, ascending; fails unless every frame is a keyframe. */
    private suspend fun sampleTimes(extractor: MediaExtractor): LongArray {
        val times = ArrayList<Long>()
        while (extractor.sampleTime >= 0) {
            currentCoroutineContext().ensureActive()
            if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC == 0) {
                throw ReverseUnsupportedException("The encoder didn't make every frame a keyframe")
            }
            times += extractor.sampleTime
            extractor.advance()
        }
        return times.sorted().toLongArray()
    }

    private suspend fun transcode(
        extractor: MediaExtractor,
        input: MediaFormat,
        times: LongArray,
        audio: StoredTrack?,
        target: File,
        onProgress: (Float) -> Unit,
    ) {
        val width = input.getInteger(MediaFormat.KEY_WIDTH)
        val height = input.getInteger(MediaFormat.KEY_HEIGHT)
        val frameRate = if (input.containsKey(MediaFormat.KEY_FRAME_RATE)) input.getInteger(MediaFormat.KEY_FRAME_RATE) else DEFAULT_FRAME_RATE
        val output = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (width.toLong() * height * frameRate * BITS_PER_PIXEL).toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1f)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val decoder = MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var completed = false
        var surface: Surface? = null
        try {
            encoder.configure(output, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()
            decoder.configure(input, surface, null, 0)
            decoder.start()

            val last = times.last()
            val info = MediaCodec.BufferInfo()
            var next = times.lastIndex
            var inputDone = false
            var decoderDone = false
            var videoTrack = -1
            var rendered = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (index >= 0) {
                        if (next < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            extractor.seekTo(times[next], MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                            val size = extractor.readSampleData(decoder.inputBuffer(index), 0).coerceAtLeast(0)
                            decoder.queueInputBuffer(index, 0, size, last - times[next], 0)
                            next--
                        }
                    }
                }
                if (!decoderDone) {
                    val index = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                    if (index >= 0) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            decoder.releaseOutputBuffer(index, false)
                            encoder.signalEndOfInputStream()
                            decoderDone = true
                        } else {
                            // Rendered with its own (reversed, rising) presentation time as the frame's timestamp.
                            decoder.releaseOutputBuffer(index, true)
                            rendered++
                            onProgress(rendered.toFloat() / times.size)
                        }
                    }
                }
                // Drain everything the encoder has, so the decoder never waits on a full surface.
                var encoderDone = false
                while (true) {
                    val index = encoder.dequeueOutputBuffer(info, if (decoderDone) CODEC_TIMEOUT_US else 0L)
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        videoTrack = muxer.addTrack(encoder.outputFormat)
                        val audioTrack = audio?.let { muxer.addTrack(it.format) }
                        muxer.start()
                        muxerStarted = true
                        if (audio != null && audioTrack != null) audio.forEach { buffer, sample -> muxer.writeSampleData(audioTrack, buffer, sample) }
                    } else if (index >= 0) {
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (info.size > 0 && !config && muxerStarted) muxer.writeSampleData(videoTrack, encoder.outputBuffer(index), info)
                        encoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                            break
                        }
                    }
                }
                if (encoderDone) break
            }
            completed = true
        } finally {
            decoder.release()
            encoder.release()
            surface?.release()
            // After a failure the file is discarded, so a muxer error mustn't hide the first one.
            if (muxerStarted) {
                if (completed) muxer.stop() else runCatching { muxer.stop() }
            }
            muxer.release()
        }
    }

    private companion object {
        const val DEFAULT_FRAME_RATE = 30
        /** About 12 Mbps at 1080p30. */
        const val BITS_PER_PIXEL = 0.2f
    }
}
