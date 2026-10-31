package com.savie.precut.media.cutout

import android.content.Context
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.ProxyManager
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.frame.Cutout
import com.savie.precut.model.frame.cutoutRangeMs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** What [MatteMaker.make] came back with. */
sealed interface MatteResult {
    /** The clip's [cutout], covering its range. */
    data class Done(val cutout: Cutout) : MatteResult

    /** No person anywhere in the clip; nothing is kept. */
    data object NoPerson : MatteResult

    data class Failed(val error: Throwable) : MatteResult
}

/**
 * Remove background (Phase 22A): works out a clip's matte on the phone. Each frame of the clip's
 * range (at most 30 a second) is sampled upright to 256 × 256, run through [PersonSegmenter],
 * steadied ([MatteSmoother]) and written to a [MatteFile] in `files/mattes`, cached by media,
 * range and recipe so making it again is instant. Proxies pause meanwhile, as for Reverse.
 */
@Singleton
class MatteMaker @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val thumbnails: ThumbnailLoader,
    private val proxies: ProxyManager,
) {

    /** [clip]'s matte; [onProgress] gets 0..1 on a worker thread. Cancellable. */
    suspend fun make(clip: Clip, onProgress: (Float) -> Unit): MatteResult {
        val range = clip.cutoutRangeMs()
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val target = File(dir, "${cacheName(clip.uri, range)}.matte")
        fun done() = MatteResult.Done(
            Cutout(
                Uri.fromFile(target).toString(), clip.uri, range.first, range.last,
                // Making it again (Update) keeps the edge the user set.
                softness = clip.cutout?.softness ?: Cutout.DEFAULT_SOFTNESS,
                edge = clip.cutout?.edge ?: 0f,
            ),
        )
        if (withContext(ioDispatcher) { MatteFile.Reader.open(target)?.use { true } } == true) return done()
        val partial = File(dir, target.name + ".part")
        withContext(Dispatchers.Main) { proxies.pause() }
        return try {
            val found = withContext(Dispatchers.Default) {
                PersonSegmenter.create(context).use { segmenter ->
                    MatteFile.Writer(partial, SegmenterInput.SIDE, SegmenterInput.SIDE).use { writer ->
                        val found = if (clip.type == ClipType.IMAGE) photo(clip, segmenter, writer) else video(clip, range, segmenter, writer, onProgress)
                        if (writer.frameCount == 0) throw IOException("No frames decoded")
                        writer.finish()
                        found
                    }
                }
            }
            when {
                !found -> MatteResult.NoPerson
                !partial.renameTo(target) -> MatteResult.Failed(IOException("Couldn't keep the matte"))
                else -> done()
            }
        } catch (e: CancellationException) {
            throw e // also an IllegalStateException; it must not read as a failure
        } catch (e: Exception) {
            // Codecs and extractors fail in many ways across phones; a failed matte says so.
            Log.w(TAG, "Matte failed", e)
            MatteResult.Failed(e)
        } catch (e: LinkageError) {
            // LiteRT's native library isn't usable on this phone (missing, or failed to start).
            Log.w(TAG, "Segmenter unavailable", e)
            MatteResult.Failed(e)
        } finally {
            // NonCancellable: this cleanup must run when the user cancels, too.
            withContext(NonCancellable + ioDispatcher) { partial.delete() }
            withContext(NonCancellable + Dispatchers.Main) { proxies.resume() }
        }
    }

    private suspend fun photo(clip: Clip, segmenter: PersonSegmenter, writer: MatteFile.Writer): Boolean {
        val key = ThumbnailKey(clip.uri, ClipType.IMAGE, 0L, PHOTO_HEIGHT_PX, clip.sourceRotationDegrees, exact = true)
        val bitmap = thumbnails.load(key, cache = false) ?: throw IOException("Can't read the photo")
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        val rgb = FloatArray(PIXELS * 3)
        val confidence = FloatArray(PIXELS)
        val bytes = ByteArray(PIXELS)
        FrameSampler.sampleArgb(pixels, bitmap.width, bitmap.height, rgb)
        segmenter.segment(rgb, confidence)
        MatteSmoother(SegmenterInput.SIDE, SegmenterInput.SIDE).next(confidence, bytes)
        writer.add(0L, bytes)
        return MatteSmoother.personShare(bytes) >= MIN_PERSON_SHARE
    }

    private suspend fun video(clip: Clip, range: LongRange, segmenter: PersonSegmenter, writer: MatteFile.Writer, onProgress: (Float) -> Unit): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, clip.uri.toUri(), null)
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
            val startUs = range.first * 1_000
            val endUs = range.last * 1_000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val rgb = FloatArray(PIXELS * 3)
            val confidence = FloatArray(PIXELS)
            val bytes = ByteArray(PIXELS)
            val smoother = MatteSmoother(SegmenterInput.SIDE, SegmenterInput.SIDE)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var lastKeptUs = Long.MIN_VALUE
            var colours: Colours? = null
            var found = false
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index) ?: throw IOException("No input buffer")
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > endUs + SLACK_US) {
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
                // Frames before the range (decoded from the keyframe before it) are skipped, and
                // at most 30 a second are kept: the matte shows its nearest picture in between.
                val wanted = info.size > 0 && timeUs >= startUs - SLACK_US && timeUs <= endUs + SLACK_US && timeUs - lastKeptUs >= MIN_GAP_US
                val image = if (wanted) decoder.getOutputImage(index) else null
                if (image != null) {
                    image.use {
                        val c = colours ?: colours(decoder.outputFormat, format, it).also { picked -> colours = picked }
                        val crop = it.cropRect
                        val planes = it.planes.map { plane -> PlaneView(plane.buffer, plane.rowStride, plane.pixelStride) }
                        FrameSampler.sampleYuv(planes[0], planes[1], planes[2], crop.left, crop.top, crop.width(), crop.height(), rotation, c.bt709, c.fullRange, rgb)
                    }
                    segmenter.segment(rgb, confidence)
                    smoother.next(confidence, bytes)
                    if (MatteSmoother.personShare(bytes) >= MIN_PERSON_SHARE) found = true
                    writer.add(timeUs, bytes)
                    lastKeptUs = timeUs
                    onProgress(((timeUs - startUs).toFloat() / (endUs - startUs).coerceAtLeast(1L)).coerceIn(0f, 1f))
                }
                decoder.releaseOutputBuffer(index, false)
                if (ended || timeUs > endUs + SLACK_US) break
            }
            return found
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

    private fun cacheName(uri: String, range: LongRange): String =
        MessageDigest.getInstance("SHA-1").digest("$uri|${range.first}|${range.last}|${PersonSegmenter.MATTE_VERSION}".toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "MatteMaker"
        const val DIR = "mattes"
        const val PIXELS = SegmenterInput.SIDE * SegmenterInput.SIDE
        const val TIMEOUT_US = 10_000L
        /** Frames this close to the range still count (rounding of ms ranges). */
        const val SLACK_US = 34_000L
        /** At most 30 matte pictures a second. */
        const val MIN_GAP_US = 33_000L
        /** A picture with at least this much person counts as finding one (0.5%). */
        const val MIN_PERSON_SHARE = 0.005f
        /** Photos are read at this height (upright) before sampling. */
        const val PHOTO_HEIGHT_PX = 512
    }
}
