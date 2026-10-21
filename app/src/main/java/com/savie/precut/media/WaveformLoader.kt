package com.savie.precut.media

import android.content.Context
import android.util.LruCache
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.audio.PcmDecoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
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

    private suspend fun decode(uri: String): FloatArray? = try {
        var accumulator: PeakAccumulator? = null
        val found = PcmDecoder.decode(
            context, uri,
            onFormat = { sampleRate, channels -> accumulator = PeakAccumulator(framesPerBucket(sampleRate), channels) },
        ) { samples, count, _ -> accumulator?.add(samples, count) }
        if (found) accumulator?.result() else null
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null // unsupported codec, revoked permission, corrupt file
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
    }
}
