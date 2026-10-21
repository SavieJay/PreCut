package com.savie.precut.media.audio

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.audio.BeatResult
import com.savie.precut.model.audio.BeatTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Finds a song's beats; times are in the file's own (source) time. */
interface BeatDetector {
    /** [uri]'s beats ([BeatResult.NONE] for silence or no rhythm); null when its sound can't be read. */
    suspend fun detect(uri: String): BeatResult?
}

/**
 * Beats found on the phone ([BeatTracker]: signal processing, no AI). Results are kept per file,
 * so choosing another density doesn't decode again.
 */
@Singleton
class AndroidBeatDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BeatDetector {
    private val cache = LruCache<String, BeatResult>(CACHED)

    override suspend fun detect(uri: String): BeatResult? {
        cache.get(uri)?.let { return it }
        return withContext(ioDispatcher) {
            try {
                var tracker: BeatTracker? = null
                var format: Pair<Int, Int>? = null
                var started = false
                val found = PcmDecoder.decode(
                    context, uri,
                    onFormat = { sampleRate, channels ->
                        // The decoder may correct the container's format before any samples arrive.
                        if (!started) format = sampleRate to channels
                    },
                ) { samples, count, timeUs ->
                    val current = tracker ?: format?.let { (rate, channels) -> BeatTracker(rate, channels, startMs = timeUs / 1_000) }?.also { tracker = it }
                    started = true
                    current?.add(samples, count)
                }
                if (!found) return@withContext null
                (tracker?.finish() ?: BeatResult.NONE).also { cache.put(uri, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't read $uri", e)
                null
            } catch (e: RuntimeException) {
                // Unsupported codec, revoked permission, corrupt file.
                Log.w(TAG, "Couldn't decode $uri", e)
                null
            }
        }
    }

    private companion object {
        const val TAG = "BeatDetector"
        const val CACHED = 8
    }
}
