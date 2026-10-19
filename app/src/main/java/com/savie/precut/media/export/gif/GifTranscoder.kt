package com.savie.precut.media.export.gif

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

/**
 * Turns an exported MP4 (already at the GIF's size and frame rate) into a looping GIF: decodes each
 * frame to CPU YUV with MediaCodec, converts it to ARGB ([YuvConverter]) and hands it to
 * [GifEncoder], each frame lasting until the next one's timestamp.
 */
class GifTranscoder {

    /**
     * Writes [target] from [source], exported at [frameRate]; [onProgress] gets 0..1. Throws
     * [IOException] when the video can't be read.
     */
    suspend fun transcode(source: File, target: File, frameRate: Int, onProgress: (Float) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IOException("No video track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("No video type")
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()
            // Portrait exports are stored as landscape frames to be turned on display.
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
            target.outputStream().buffered().use { out ->
                decode(extractor, decoder, Stream(format, durationUs, rotation, 1_000L / frameRate.coerceIn(1, 60)), out, onProgress)
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

    /** What the decode loop needs to know about the video. */
    private class Stream(val format: MediaFormat, val durationUs: Long, val rotationDegrees: Int, val frameMs: Long)

    private suspend fun decode(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        stream: Stream,
        out: java.io.OutputStream,
        onProgress: (Float) -> Unit,
    ) {
        val format = stream.format
        var encoder: GifEncoder? = null
        var frame: Frame? = null
        // The previous frame waits until the next one's time is known (its duration).
        var held: Pair<IntArray, Long>? = null
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!inputDone) {
                val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = decoder.getInputBuffer(index) ?: throw IOException("No input buffer")
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
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
            if (info.size > 0) {
                val image = decoder.getOutputImage(index)
                if (image != null) {
                    val decoded = image.use { img ->
                        val f = frame ?: Frame(img.cropRect.width(), img.cropRect.height(), colours(decoder.outputFormat, format, img)).also { frame = it }
                        f.read(img)
                    }
                    val f = frame ?: throw IOException("No frame")
                    val upright = FrameRotation.rotate(decoded, f.width, f.height, stream.rotationDegrees)
                    val current = upright.pixels
                    val gif = encoder ?: GifEncoder(out, upright.width, upright.height).also { encoder = it }
                    held?.let { (pixels, timeUs) -> gif.addFrame(pixels, ((info.presentationTimeUs - timeUs) / 1_000).coerceAtLeast(1L)) }
                    held = current to info.presentationTimeUs
                    if (stream.durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / stream.durationUs).coerceIn(0f, 1f))
                }
            }
            decoder.releaseOutputBuffer(index, false)
            if (ended) break
        }
        val gif = encoder ?: throw IOException("No frames decoded")
        // The last frame lasts one frame at the GIF's own rate.
        held?.let { (pixels, _) -> gif.addFrame(pixels, stream.frameMs) }
        gif.finish()
    }

    /** A decoded frame's size and colour description, with buffers reused for every frame. */
    private class Frame(val width: Int, val height: Int, private val colours: Colours) {
        private val y = ByteArray(width * height)
        private val u = ByteArray(((width + 1) / 2) * ((height + 1) / 2))
        private val v = ByteArray(u.size)

        /** The image's visible area as ARGB (a new array: the encoder keeps the previous frame). */
        fun read(image: Image): IntArray {
            val crop = image.cropRect
            listOf(y, u, v).forEachIndexed { i, plane -> pack(image.planes[i], plane, crop.left, crop.top, if (i == 0) 0 else 1) }
            return IntArray(width * height).also { YuvConverter.toArgb(y, u, v, width, height, colours.bt709, colours.fullRange, it) }
        }

        private fun pack(plane: Image.Plane, target: ByteArray, left: Int, top: Int, shift: Int) {
            val w = (width + shift) shr shift
            val h = (height + shift) shr shift
            val source = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            for (row in 0 until h) {
                val start = ((top shr shift) + row) * rowStride + (left shr shift) * pixelStride
                if (pixelStride == 1) {
                    source.position(start)
                    source.get(target, row * w, w)
                } else {
                    for (x in 0 until w) target[row * w + x] = source.get(start + x * pixelStride)
                }
            }
        }
    }

    private data class Colours(val bt709: Boolean, val fullRange: Boolean)

    private companion object {
        const val TIMEOUT_US = 10_000L

        /** The stream's colour standard and range, else Android's rule: BT.601 up to PAL size, BT.709 above. */
        fun colours(output: MediaFormat, input: MediaFormat, image: Image): Colours {
            fun int(key: String): Int? = when {
                output.containsKey(key) -> output.getInteger(key)
                input.containsKey(key) -> input.getInteger(key)
                else -> null
            }
            val standard = int(MediaFormat.KEY_COLOR_STANDARD)
            val w = image.cropRect.width()
            val h = image.cropRect.height()
            val bt709 = standard?.let { it != MediaFormat.COLOR_STANDARD_BT601_PAL && it != MediaFormat.COLOR_STANDARD_BT601_NTSC }
                ?: !((w <= 720 && h <= 576) || (h <= 720 && w <= 576))
            return Colours(bt709, int(MediaFormat.KEY_COLOR_RANGE) == MediaFormat.COLOR_RANGE_FULL)
        }
    }
}
