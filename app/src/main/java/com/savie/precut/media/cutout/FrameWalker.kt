package com.savie.precut.media.cutout

import android.content.Context
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

/**
 * Walks a video's frames between two source times with MediaCodec (CPU images, like the GIF
 * transcoder) and hands those [walk]'s `wanted` keeps to `onFrame`, sampled upright into the
 * segmenter's input ([FrameSampler]). Shared by background removal and Auto reframe.
 */
internal object FrameWalker {
    private const val TIMEOUT_US = 10_000L

    /**
     * Decodes [uri]'s video track from [startUs] to [endUs]; frames before [startUs] (decoded from
     * the keyframe before it) are skipped. Throws `IOException` or a `RuntimeException` when the
     * file can't be read or decoded. Cancellable between frames.
     */
    suspend fun walk(
        context: Context,
        uri: String,
        startUs: Long,
        endUs: Long,
        wanted: (timeUs: Long) -> Boolean,
        onFrame: (timeUs: Long, rgb: FloatArray) -> Unit,
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri.toUri(), null)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IOException("No video track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("No video type")
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()
            extractor.seekTo(startUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val rgb = FloatArray(SegmenterInput.SIDE * SegmenterInput.SIDE * 3)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var colours: Colours? = null
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index) ?: throw IOException("No input buffer")
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > endUs) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index < 0) continue
                val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                val timeUs = info.presentationTimeUs
                val image = if (info.size > 0 && timeUs in startUs..endUs && wanted(timeUs)) decoder.getOutputImage(index) else null
                if (image != null) {
                    image.use {
                        val c = colours ?: colours(decoder.outputFormat, format, it).also { picked -> colours = picked }
                        val crop = it.cropRect
                        val planes = it.planes.map { plane -> PlaneView(plane.buffer, plane.rowStride, plane.pixelStride) }
                        FrameSampler.sampleYuv(planes[0], planes[1], planes[2], crop.left, crop.top, crop.width(), crop.height(), rotation, c.bt709, c.fullRange, rgb)
                    }
                    onFrame(timeUs, rgb)
                }
                decoder.releaseOutputBuffer(index, false)
                if (ended || timeUs > endUs) break
            }
        } finally {
            codec?.let {
                try {
                    it.stop()
                } catch (_: IllegalStateException) {
                    // Never started, or already failed.
                }
                it.release()
            }
            extractor.release()
        }
    }

    private data class Colours(val bt709: Boolean, val fullRange: Boolean)

    /** The stream's colour standard and range, else Android's rule: BT.601 up to PAL size, BT.709 above. */
    private fun colours(output: MediaFormat, input: MediaFormat, image: Image): Colours {
        fun int(key: String): Int? = when {
            output.containsKey(key) -> output.getInteger(key)
            input.containsKey(key) -> input.getInteger(key)
            else -> null
        }
        val w = image.cropRect.width()
        val h = image.cropRect.height()
        val bt709 = int(MediaFormat.KEY_COLOR_STANDARD)?.let { it != MediaFormat.COLOR_STANDARD_BT601_PAL && it != MediaFormat.COLOR_STANDARD_BT601_NTSC }
            ?: !((w <= 720 && h <= 576) || (h <= 720 && w <= 576))
        return Colours(bt709, int(MediaFormat.KEY_COLOR_RANGE) == MediaFormat.COLOR_RANGE_FULL)
    }
}
