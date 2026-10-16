package com.savie.precut.media.layer

import android.content.Context
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES20
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Decodes one video file into three GL textures (Y, U and V planes) for a layer drawn by
 * [OverlayLayersShaderProgram]; the layer converts them to RGB. Runs entirely on the GL thread.
 *
 * Frames come out of MediaCodec as CPU images rather than through a SurfaceTexture: decoding a
 * second video into an external texture inside Media3's frame processor corrupted the main
 * video's frames (it showed the overlay's frames), and plain textures behave the same everywhere.
 *
 * [frameAt] shows the latest frame at or before a source time (within half a frame), decoding
 * forward from the current position, or seeking when asked for an earlier or far-away time.
 */
@OptIn(UnstableApi::class)
internal class VideoFrameSource private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    /** Rotation that turns the decoded picture upright. */
    val rotationDegrees: Int,
    private val frameDurationUs: Long,
    /** BT.709 (HD) rather than BT.601 colours; refined from the decoder's output format. */
    bt709: Boolean,
    /** Full-range (0–255) luma rather than video range (16–235). */
    fullRange: Boolean,
) {
    var bt709 = bt709
        private set
    var fullRange = fullRange
        private set

    private val info = MediaCodec.BufferInfo()
    private var inputEnded = false
    private var outputEnded = false
    /** A decoded frame later than the last request, held so it isn't lost. */
    private var heldIndex = -1
    private var heldUs = 0L
    private var shownUs = UNSET

    /** Plane textures (Y, U, V); created with the first frame. */
    val planes = IntArray(3) { NO_TEXTURE }

    /** Visible size of the decoded picture (before [rotationDegrees]). */
    var width = 0
        private set
    var height = 0
        private set

    private var packed: ByteBuffer? = null

    /** Whether any frame has been shown yet. */
    val hasFrame: Boolean get() = shownUs != UNSET

    /** Brings the frame for source time [sourceUs] into the plane textures. */
    fun frameAt(sourceUs: Long) {
        try {
            val target = sourceUs + frameDurationUs / 2
            if (shownUs == UNSET) {
                seekTo(sourceUs)
            } else {
                val furthest = if (heldIndex >= 0) maxOf(heldUs, shownUs) else shownUs
                when {
                    // Earlier than what's on screen, or too far ahead to decode through: seek.
                    sourceUs < shownUs - frameDurationUs / 2 || sourceUs > furthest + SEEK_AHEAD_US -> seekTo(sourceUs)
                    // The next decoded frame isn't due yet: the one on screen stays.
                    heldIndex >= 0 && heldUs > target -> return
                    // Past the last frame: it stays.
                    outputEnded && heldIndex < 0 -> return
                }
            }
            decodeUpTo(target)
        } catch (e: IllegalStateException) {
            // A codec in a bad state keeps the last frame; the rest of the frame still renders.
            Log.w(TAG, "Decoding failed", e)
        } catch (e: GlUtil.GlException) {
            Log.w(TAG, "Upload failed", e)
        }
    }

    private fun seekTo(sourceUs: Long) {
        if (heldIndex >= 0) codec.releaseOutputBuffer(heldIndex, false)
        heldIndex = -1
        codec.flush()
        extractor.seekTo(sourceUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        inputEnded = false
        outputEnded = false
    }

    private fun decodeUpTo(targetUs: Long) {
        var candidate = -1
        var candidateUs = 0L
        if (heldIndex >= 0 && heldUs <= targetUs) {
            candidate = heldIndex
            candidateUs = heldUs
            heldIndex = -1
        }
        val deadline = System.nanoTime() + DECODE_BUDGET_NS
        while (heldIndex < 0 && !outputEnded && System.nanoTime() < deadline) {
            feedInput()
            val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            if (index < 0) continue // Try again, or a format/buffers change.
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
            if (info.size <= 0 && outputEnded) {
                codec.releaseOutputBuffer(index, false)
                break
            }
            if (info.presentationTimeUs <= targetUs) {
                if (candidate >= 0) codec.releaseOutputBuffer(candidate, false)
                candidate = index
                candidateUs = info.presentationTimeUs
            } else {
                heldIndex = index
                heldUs = info.presentationTimeUs
            }
        }
        if (candidate >= 0) show(candidate, candidateUs)
    }

    private fun feedInput() {
        if (inputEnded) return
        while (true) {
            val index = codec.dequeueInputBuffer(0)
            if (index < 0) return
            val buffer = codec.getInputBuffer(index) ?: return
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputEnded = true
                return
            }
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    private fun show(index: Int, presentationUs: Long) {
        try {
            codec.getOutputImage(index)?.use(::upload)
        } finally {
            codec.releaseOutputBuffer(index, false)
        }
        shownUs = presentationUs
    }

    /** Uploads the image's visible area, plane by plane, tightly packed. */
    private fun upload(image: Image) {
        val crop = image.cropRect
        val w = crop.width()
        val h = crop.height()
        if (w != width || h != height) {
            width = w
            height = h
            // The bitstream's own colour description, when the decoder found one.
            val output = codec.outputFormat
            if (output.containsKey(MediaFormat.KEY_COLOR_STANDARD)) bt709 = output.getInteger(MediaFormat.KEY_COLOR_STANDARD) != MediaFormat.COLOR_STANDARD_BT601_PAL &&
                output.getInteger(MediaFormat.KEY_COLOR_STANDARD) != MediaFormat.COLOR_STANDARD_BT601_NTSC
            if (output.containsKey(MediaFormat.KEY_COLOR_RANGE)) fullRange = output.getInteger(MediaFormat.KEY_COLOR_RANGE) == MediaFormat.COLOR_RANGE_FULL
            planes.forEachIndexed { i, texture ->
                if (texture != NO_TEXTURE) GlUtil.deleteTexture(texture)
                planes[i] = GlUtil.generateTexture()
            }
        }
        image.planes.forEachIndexed { i, plane ->
            // Chroma planes are half size in both directions (4:2:0).
            val shift = if (i == 0) 0 else 1
            val planeW = (w + shift) shr shift
            val planeH = (h + shift) shr shift
            val left = crop.left shr shift
            val top = crop.top shr shift
            val buffer = packedBuffer(planeW * planeH)
            val source = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            for (row in 0 until planeH) {
                val start = (top + row) * rowStride + left * pixelStride
                if (pixelStride == 1) {
                    source.limit(start + planeW)
                    source.position(start)
                    buffer.put(source)
                    source.limit(source.capacity())
                } else {
                    for (x in 0 until planeW) buffer.put(source.get(start + x * pixelStride))
                }
            }
            buffer.flip()
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GlUtil.bindTexture(GLES20.GL_TEXTURE_2D, planes[i], GLES20.GL_LINEAR)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, planeW, planeH, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buffer)
            GlUtil.checkGlError()
        }
    }

    private fun packedBuffer(size: Int): ByteBuffer {
        val current = packed
        if (current != null && current.capacity() >= size) return current.also { it.clear() }
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder()).also { packed = it }
    }

    fun release() {
        try {
            codec.stop()
        } catch (_: IllegalStateException) {
            // Already stopped or failed.
        }
        codec.release()
        extractor.release()
        planes.forEach { texture ->
            if (texture != NO_TEXTURE) {
                try {
                    GlUtil.deleteTexture(texture)
                } catch (_: GlUtil.GlException) {
                    // The context is going away anyway.
                }
            }
        }
        planes.fill(NO_TEXTURE)
    }

    companion object {
        private const val TAG = "VideoFrameSource"
        private const val UNSET = Long.MIN_VALUE
        private const val NO_TEXTURE = -1

        /** Requests further ahead than this seek instead of decoding everything in between. */
        private const val SEEK_AHEAD_US = 1_500_000L
        private const val DEQUEUE_TIMEOUT_US = 5_000L
        /** How long one request may decode before showing what it has (a seek to a late frame). */
        private val DECODE_BUDGET_NS = TimeUnit.SECONDS.toNanos(2)

        /** Android's default for undescribed video (ColorUtils): BT.601 up to PAL size, BT.709 above. */
        fun isStandardDefinition(width: Int, height: Int): Boolean =
            (width <= 720 && height <= 576) || (height <= 720 && width <= 576)

        /** Opens [uri]'s first video track; null when it can't be read or decoded. */
        fun open(context: Context, uri: String): VideoFrameSource? {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(context, Uri.parse(uri), null)
                val track = (0 until extractor.trackCount).firstOrNull { i ->
                    extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                }
                val format = track?.let(extractor::getTrackFormat)
                val mime = format?.getString(MediaFormat.KEY_MIME)
                if (track == null || format == null || mime == null) {
                    extractor.release()
                    return null
                }
                extractor.selectTrack(track)
                val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
                val fps = frameRateOf(format)
                val width = format.getInteger(MediaFormat.KEY_WIDTH)
                val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                val standard = if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) format.getInteger(MediaFormat.KEY_COLOR_STANDARD) else 0
                val range = if (format.containsKey(MediaFormat.KEY_COLOR_RANGE)) format.getInteger(MediaFormat.KEY_COLOR_RANGE) else 0
                // CPU-readable frames (Image planes) instead of a surface.
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
                decoder.configure(format, null, null, 0)
                decoder.start()
                return VideoFrameSource(
                    extractor, decoder,
                    rotationDegrees = rotation,
                    frameDurationUs = (1_000_000f / fps.coerceIn(1f, 240f)).toLong(),
                    bt709 = if (standard != 0) standard != MediaFormat.COLOR_STANDARD_BT601_PAL && standard != MediaFormat.COLOR_STANDARD_BT601_NTSC else !isStandardDefinition(width, height),
                    fullRange = range == MediaFormat.COLOR_RANGE_FULL,
                )
            } catch (e: IOException) {
                Log.w(TAG, "Can't open $uri", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Can't decode $uri", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Can't decode $uri", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "No access to $uri", e)
            }
            codec?.release()
            extractor.release()
            return null
        }
    }
}

/** The track's frame rate, stored as an int or a float depending on the extractor; 30 when absent. */
private fun frameRateOf(format: MediaFormat): Float {
    if (!format.containsKey(MediaFormat.KEY_FRAME_RATE)) return 30f
    return try {
        format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
    } catch (_: ClassCastException) {
        format.getFloat(MediaFormat.KEY_FRAME_RATE)
    }
}
