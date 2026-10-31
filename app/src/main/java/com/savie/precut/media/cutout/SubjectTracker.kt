package com.savie.precut.media.cutout

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.ProxyManager
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.frame.SubjectSample
import com.savie.precut.model.frame.cutoutCoversClip
import com.savie.precut.model.frame.matteMsAt
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineOffsetToSourceMs
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
import javax.inject.Inject
import javax.inject.Singleton

/** What [SubjectTracker.track] came back with. */
sealed interface TrackResult {
    data class Done(val samples: List<SubjectSample>) : TrackResult

    /** No person anywhere in the clip. */
    data object NoPerson : TrackResult

    data class Failed(val error: Throwable) : TrackResult
}

/**
 * Auto reframe (Phase 22B): where the person is through a clip, about six times a second of the
 * timeline, on the phone. A clip whose background is removed reads its matte (nothing to decode);
 * others go through [FrameWalker] and [PersonSegmenter] like a matte. Proxies pause meanwhile.
 */
@Singleton
class SubjectTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val thumbnails: ThumbnailLoader,
    private val proxies: ProxyManager,
) {

    /** [clip]'s person track; [onProgress] gets 0..1 on a worker thread. Cancellable. */
    suspend fun track(clip: Clip, onProgress: (Float) -> Unit): TrackResult {
        withContext(Dispatchers.Main) { proxies.pause() }
        return try {
            val samples = withContext(Dispatchers.Default) {
                when {
                    clip.type == ClipType.IMAGE -> listOf(SubjectSample(0L, photoCentre(clip)))
                    clip.cutout != null && clip.cutoutCoversClip() -> fromMatte(clip, onProgress) ?: fromVideo(clip, onProgress)
                    else -> fromVideo(clip, onProgress)
                }
            }
            if (samples.all { it.point == null }) TrackResult.NoPerson else TrackResult.Done(samples)
        } catch (e: CancellationException) {
            throw e // also an IllegalStateException; it must not read as a failure
        } catch (e: Exception) {
            Log.w(TAG, "Tracking failed", e)
            TrackResult.Failed(e)
        } catch (e: LinkageError) {
            Log.w(TAG, "Segmenter unavailable", e)
            TrackResult.Failed(e)
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { proxies.resume() }
        }
    }

    /** The moments to look at: every [STEP_MS] of the clip's timeline, as source times. */
    private fun moments(clip: Clip): List<Long> {
        val length = clip.timelineDurationMs()
        return ((0L until length step STEP_MS) + length).map(clip::timelineOffsetToSourceMs).distinct()
    }

    private suspend fun photoCentre(clip: Clip) = PersonSegmenter.create(context).use { segmenter ->
        val key = ThumbnailKey(clip.uri, ClipType.IMAGE, 0L, PHOTO_HEIGHT_PX, clip.sourceRotationDegrees, exact = true)
        val bitmap = thumbnails.load(key, cache = false) ?: throw IOException("Can't read the photo")
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        val rgb = FloatArray(PIXELS * 3)
        val confidence = FloatArray(PIXELS)
        FrameSampler.sampleArgb(pixels, bitmap.width, bitmap.height, rgb)
        segmenter.segment(rgb, confidence)
        SubjectFinder.centre(confidence, SegmenterInput.SIDE, SegmenterInput.SIDE)
    }

    /** From the clip's background-removal matte; null when it can't be read. */
    private suspend fun fromMatte(clip: Clip, onProgress: (Float) -> Unit): List<SubjectSample>? {
        val path = clip.cutout?.matteUri?.toUri()?.path ?: return null
        val reader = withContext(ioDispatcher) { MatteFile.Reader.open(File(path)) } ?: return null
        return reader.use { matte ->
            val picture = ByteArray(matte.width * matte.height)
            val moments = moments(clip)
            moments.mapIndexed { i, sourceMs ->
                currentCoroutineContext().ensureActive()
                onProgress(i.toFloat() / moments.size)
                val point = clip.matteMsAt(sourceMs)?.let { matteMs ->
                    matte.read(matte.indexAt(matteMs * 1_000, toleranceUs = HALF_FRAME_US), picture)
                    SubjectFinder.centre(picture, matte.width, matte.height)
                }
                SubjectSample(sourceMs, point)
            }
        }
    }

    /** Decoded and segmented at each moment (the first frame at or after it). */
    private suspend fun fromVideo(clip: Clip, onProgress: (Float) -> Unit): List<SubjectSample> {
        val moments = moments(clip)
        val samples = ArrayList<SubjectSample>(moments.size)
        PersonSegmenter.create(context).use { segmenter ->
            val confidence = FloatArray(PIXELS)
            val startUs = clip.trimStartMs * 1_000
            val endUs = clip.trimEndMs * 1_000
            FrameWalker.walk(
                context, clip.uri, startUs - HALF_FRAME_US, endUs + HALF_FRAME_US,
                wanted = { timeUs -> samples.size < moments.size && timeUs >= moments[samples.size] * 1_000 - HALF_FRAME_US },
            ) { timeUs, rgb ->
                segmenter.segment(rgb, confidence)
                val point = SubjectFinder.centre(confidence, SegmenterInput.SIDE, SegmenterInput.SIDE)
                // Frames further apart than the moments (slow footage, fast speed) stand for every moment they reach.
                while (samples.size < moments.size && moments[samples.size] * 1_000 <= timeUs + HALF_FRAME_US) {
                    samples += SubjectSample(moments[samples.size], point)
                }
                onProgress(samples.size.toFloat() / moments.size)
            }
        }
        // Moments past the last decoded frame keep the last place found.
        while (samples.size < moments.size) samples += SubjectSample(moments[samples.size], samples.lastOrNull()?.point)
        return samples
    }

    private companion object {
        const val TAG = "SubjectTracker"
        const val PIXELS = SegmenterInput.SIDE * SegmenterInput.SIDE
        /** About six looks a second of the timeline. */
        const val STEP_MS = 167L
        const val HALF_FRAME_US = 16_700L
        const val PHOTO_HEIGHT_PX = 512
    }
}
