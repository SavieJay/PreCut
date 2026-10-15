package com.savie.precut.media.reverse

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.ProxyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** How a reverse ended. */
sealed interface ReverseResult {
    data class Done(val uri: String) : ReverseResult

    /** This phone's codecs can't do it (e.g. no all-keyframe encoding). */
    data object Unsupported : ReverseResult

    data class Failed(val cause: Throwable) : ReverseResult
}

/**
 * Makes a reversed copy of a clip's range (T-19) in `files/reversed`, cached by source and range:
 * 1. Transformer re-encodes the range with every frame a keyframe (≤ 1080p, SDR);
 * 2. its sound is reversed ([AudioReverser]);
 * 3. its frames are decoded last to first into a new file with the sound ([VideoReverser]).
 * Preview proxies pause meanwhile so the two don't compete for the encoder.
 */
@OptIn(UnstableApi::class)
@Singleton
class ClipReverser @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val proxies: ProxyManager,
) {

    /** Reverses [uri] between [startMs] and [endMs]; [onProgress] gets 0..1 on any thread. Cancellable. */
    suspend fun reverse(uri: String, startMs: Long, endMs: Long, shortSidePx: Int, onProgress: (Float) -> Unit): ReverseResult {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val target = File(dir, "${cacheName(uri, startMs, endMs)}.mp4")
        if (target.exists()) return ReverseResult.Done(Uri.fromFile(target).toString())
        val work = File(context.cacheDir, "reverse").apply { deleteRecursively(); mkdirs() }
        val partial = File(dir, target.name + ".part")
        withContext(Dispatchers.Main) { proxies.pause() }
        return try {
            val intra = File(work, "intra.mp4")
            allKeyframes(uri, startMs, endMs, shortSidePx, intra) { onProgress(it * INTRA_SHARE) }
            withContext(ioDispatcher) {
                val audio = AudioReverser(work).reverse(intra)
                onProgress(INTRA_SHARE + AUDIO_SHARE)
                VideoReverser().reverse(intra, audio, partial) { onProgress(INTRA_SHARE + AUDIO_SHARE + it * (1f - INTRA_SHARE - AUDIO_SHARE)) }
                if (!partial.renameTo(target)) throw IllegalStateException("Couldn't keep the reversed file")
            }
            ReverseResult.Done(Uri.fromFile(target).toString())
        } catch (e: CancellationException) {
            throw e // also an IllegalStateException; it must not read as a failure
        } catch (e: ReverseUnsupportedException) {
            ReverseResult.Unsupported
        } catch (e: IOException) {
            ReverseResult.Failed(e)
        } catch (e: ExportException) {
            ReverseResult.Failed(e)
        } catch (e: IllegalStateException) {
            // MediaCodec reports codec failures as IllegalStateException (CodecException).
            ReverseResult.Failed(e)
        } finally {
            // NonCancellable: this cleanup must run when the user cancels, too.
            withContext(NonCancellable + ioDispatcher) {
                partial.delete()
                work.deleteRecursively()
            }
            withContext(NonCancellable + Dispatchers.Main) { proxies.resume() }
        }
    }

    /** Step 1, on the main thread (Transformer needs a Looper); cancelling cancels the export. */
    private suspend fun allKeyframes(uri: String, startMs: Long, endMs: Long, shortSidePx: Int, target: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Main) {
            coroutineScope {
                lateinit var transformer: Transformer
                val poller = launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        delay(PROGRESS_POLL_MS)
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    }
                }
                try {
                    suspendCancellableCoroutine { continuation ->
                        transformer = Transformer.Builder(context)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setEncoderFactory(
                                DefaultEncoderFactory.Builder(context)
                                    .setRequestedVideoEncoderSettings(
                                        // 0 asks for every frame to be a keyframe (MediaFormat.KEY_I_FRAME_INTERVAL).
                                        VideoEncoderSettings.Builder().setBitrate(INTRA_BITRATE).setiFrameIntervalSeconds(0f).build(),
                                    )
                                    .setEnableFallback(true)
                                    .build(),
                            )
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) = continuation.resume(Unit)

                                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) =
                                    continuation.resumeWithException(exportException)
                            })
                            .build()
                        val media = MediaItem.Builder()
                            .setUri(uri)
                            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build())
                            .build()
                        val item = EditedMediaItem.Builder(media)
                            .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(shortSidePx))))
                            .build()
                        val composition = Composition.Builder(
                            EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)).addItem(item).build(),
                        ).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
                        // Cancellation can arrive on any thread; Transformer is used from the main one.
                        continuation.invokeOnCancellation { mainHandler.post { transformer.cancel() } }
                        transformer.start(composition, target.absolutePath)
                    }
                } finally {
                    poller.cancel()
                }
            }
        }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun cacheName(uri: String, startMs: Long, endMs: Long): String =
        MessageDigest.getInstance("SHA-1").digest("$uri|$startMs|$endMs".toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIR = "reversed"
        const val PROGRESS_POLL_MS = 250L
        const val INTRA_BITRATE = 25_000_000
        /** Shares of the whole progress bar. */
        const val INTRA_SHARE = 0.4f
        const val AUDIO_SHARE = 0.1f
    }
}
