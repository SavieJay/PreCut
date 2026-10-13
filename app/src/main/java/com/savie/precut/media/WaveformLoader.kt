package com.savie.precut.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import com.savie.precut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio waveforms: peak amplitude (0..1) per [BUCKET_MS] of source time. Decoded once on-device
 * (MediaExtractor + MediaCodec), then cached in memory and on disk. Decodes run one at a time.
 */
@Singleton
class WaveformLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val memory = LruCache<String, FloatArray>(24)
    private val lock = Mutex()

    fun cached(uri: String): FloatArray? = memory.get(uri)

    /** Peaks for [uri], or null if it has no readable audio. */
    suspend fun load(uri: String): FloatArray? {
        memory.get(uri)?.let { return it }
        return withContext(ioDispatcher) {
            lock.withLock {
                memory.get(uri) ?: (readDisk(uri) ?: decode(uri)?.also { writeDisk(uri, it) })?.also { memory.put(uri, it) }
            }
        }
    }

    private suspend fun decode(uri: String): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            return drain(extractor, decoder, format)
        } catch (_: IOException) {
            return null
        } catch (_: RuntimeException) {
            return null // unsupported codec, revoked permission, corrupt file
        } finally {
            codec?.release()
            extractor.release()
        }
    }

    private suspend fun drain(extractor: MediaExtractor, decoder: MediaCodec, inputFormat: MediaFormat): FloatArray {
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var accumulator = PeakAccumulator(framesPerBucket(sampleRate), channels)
        val info = MediaCodec.BufferInfo()
        var shorts = ShortArray(8192)
        var inputDone = false
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = requireNotNull(decoder.getInputBuffer(inIndex))
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // The decoder's real output rate/channels (it may differ from the container's).
                sampleRate = decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                accumulator = PeakAccumulator(framesPerBucket(sampleRate), channels)
            } else if (outIndex >= 0) {
                val output = requireNotNull(decoder.getOutputBuffer(outIndex))
                val pcm = output.order(ByteOrder.nativeOrder()).asShortBuffer()
                val count = info.size / 2
                if (shorts.size < count) shorts = ShortArray(count)
                pcm.get(shorts, 0, count)
                accumulator.add(shorts, count)
                decoder.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        return accumulator.result()
    }

    private fun framesPerBucket(sampleRate: Int) = (sampleRate * BUCKET_MS / 1000).toInt().coerceAtLeast(1)

    private fun cacheFile(uri: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "waveforms").apply { mkdirs() }, "$hash.bin")
    }

    private fun readDisk(uri: String): FloatArray? = try {
        val bytes = cacheFile(uri).takeIf { it.isFile }?.readBytes() ?: return null
        val floats = ByteBuffer.wrap(bytes).asFloatBuffer()
        FloatArray(floats.remaining()).also { floats.get(it) }
    } catch (_: IOException) {
        null
    }

    private fun writeDisk(uri: String, peaks: FloatArray) {
        try {
            val buffer = ByteBuffer.allocate(peaks.size * 4)
            buffer.asFloatBuffer().put(peaks)
            cacheFile(uri).writeBytes(buffer.array())
        } catch (_: IOException) {
            // Cache is best-effort.
        }
    }

    companion object {
        const val BUCKET_MS = 20L
        private const val TIMEOUT_US = 10_000L
    }
}
