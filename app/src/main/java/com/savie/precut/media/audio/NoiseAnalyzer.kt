package com.savie.precut.media.audio

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What [NoiseAnalyzer.analyze] came back with. */
sealed interface NoiseAnalysis {
    /** The noise's level per band, for `NoiseReduction.profile`. */
    data class Done(val profile: List<Float>) : NoiseAnalysis

    /** No audio track, or too little sound to measure. */
    data object NoSound : NoiseAnalysis

    data class Failed(val error: Throwable) : NoiseAnalysis
}

/**
 * Reduce noise (Phase 22B): measures the background noise of a file's sound between two source
 * times, on the phone ([NoiseProfileBuilder] on the sound mixed to mono). Nothing is uploaded.
 */
@Singleton
class NoiseAnalyzer @Inject constructor(@ApplicationContext private val context: Context) {

    /** [uri]'s noise between [startMs] and [endMs]; [onProgress] gets 0..1 on a worker thread. Cancellable. */
    suspend fun analyze(uri: String, startMs: Long, endMs: Long, onProgress: (Float) -> Unit): NoiseAnalysis = withContext(Dispatchers.Default) {
        try {
            var builder: NoiseProfileBuilder? = null
            var channels = 1
            var mono = FloatArray(0)
            val startUs = startMs * 1_000
            val endUs = endMs * 1_000
            val found = PcmDecoder.decode(
                context, uri,
                onFormat = { rate, count ->
                    // The decoder's own format can differ from the container's; start again with it.
                    builder = NoiseProfileBuilder(rate)
                    channels = count.coerceAtLeast(1)
                },
                startUs = startUs,
                endUs = endUs,
            ) { samples, count, timeUs ->
                val target = builder ?: return@decode
                if (timeUs < startUs || timeUs > endUs) return@decode
                val frames = count / channels
                if (mono.size < frames) mono = FloatArray(frames)
                for (f in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until channels) sum += samples[f * channels + c]
                    mono[f] = sum / channels / 32_768f
                }
                target.add(mono, frames)
                onProgress(((timeUs - startUs).toFloat() / (endUs - startUs).coerceAtLeast(1L)).coerceIn(0f, 1f))
            }
            val profile = builder?.build()
            if (!found || profile == null) NoiseAnalysis.NoSound else NoiseAnalysis.Done(profile)
        } catch (e: CancellationException) {
            throw e // also an IllegalStateException; it must not read as a failure
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read the sound", e)
            NoiseAnalysis.Failed(e)
        } catch (e: RuntimeException) {
            // Unsupported codec, revoked access, a corrupt file.
            Log.w(TAG, "Couldn't decode the sound", e)
            NoiseAnalysis.Failed(e)
        }
    }

    private companion object {
        const val TAG = "NoiseAnalyzer"
    }
}
