package com.savie.precut.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.ClipType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Identifies one thumbnail. [sourceMs] is quantized so nearby requests share a frame. */
data class ThumbnailKey(
    val uri: String,
    val type: ClipType,
    val sourceMs: Long,
    val heightPx: Int,
    /** Rotation to apply for images (EXIF); video frames come out already rotated. */
    val rotationDegrees: Int = 0,
    /** The exact frame at [sourceMs] (slower) instead of the nearest keyframe; for covers. */
    val exact: Boolean = false,
)

/**
 * Low-resolution frames for the timeline, with an LRU memory cache sized to 1/8 of the heap.
 * Extraction is serialized behind one lock: MediaMetadataRetriever isn't thread-safe, and a few
 * open retrievers are kept so scrolling one clip doesn't reopen the file for every frame.
 */
@Singleton
class ThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val cache = object : LruCache<ThumbnailKey, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: ThumbnailKey, value: Bitmap) = value.byteCount / 1024
    }

    private val lock = Mutex()
    private val retrievers = object : LinkedHashMap<String, MediaMetadataRetriever>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaMetadataRetriever>): Boolean {
            // Called inside put(), i.e. while holding [lock], so nothing else is using it.
            val evict = size > MAX_OPEN_RETRIEVERS
            if (evict) eldest.value.release()
            return evict
        }
    }

    fun cached(key: ThumbnailKey): Bitmap? = cache.get(key.quantized())

    /**
     * Returns the thumbnail, or null if the source can't be read. Large one-off frames (covers)
     * pass [cache] = false so they don't evict the timeline's tiles.
     */
    suspend fun load(key: ThumbnailKey, cache: Boolean = true): Bitmap? {
        val quantized = key.quantized()
        this.cache.get(quantized)?.let { return it }
        return withContext(ioDispatcher) {
            lock.withLock {
                this@ThumbnailLoader.cache.get(quantized)
                    ?: extract(quantized)?.also { if (cache) this@ThumbnailLoader.cache.put(quantized, it) }
            }
        }
    }

    private fun extract(key: ThumbnailKey): Bitmap? = try {
        when (key.type) {
            ClipType.VIDEO -> videoFrame(key)
            ClipType.IMAGE -> imageThumbnail(key)
        }
    } catch (_: RuntimeException) {
        // Revoked permission, unreadable file or a decoder failure: show an empty tile instead.
        null
    } catch (_: java.io.IOException) {
        null
    }

    private fun videoFrame(key: ThumbnailKey): Bitmap? {
        val retriever = retrievers.getOrPut(key.uri) {
            MediaMetadataRetriever().apply { setDataSource(context, Uri.parse(key.uri)) }
        }
        fun meta(k: Int) = retriever.extractMetadata(k)?.toIntOrNull() ?: 0
        val width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        val timeUs = key.sourceMs * 1_000
        val option = if (key.exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && width > 0 && height > 0) {
            // The frame keeps its aspect ratio within the box; a square box works whether the
            // bounds apply before or after rotation. It's scaled to the exact height below.
            val box = (key.heightPx * maxOf(width, height).toFloat() / minOf(width, height)).roundToInt()
            retriever.getScaledFrameAtTime(timeUs, option, box, box)
        } else {
            retriever.getFrameAtTime(timeUs, option)
        } ?: return null
        return frame.withDisplayRotation(width, height, rotation).scaledToHeight(key.heightPx)
    }

    /**
     * Most devices return frames already rotated per the video's rotation metadata, but some
     * don't. If a 90/270° video's frame still has the encoded orientation, rotate it ourselves.
     * (A 180° miss can't be detected from the shape; frameworks reliably apply that case.)
     */
    private fun Bitmap.withDisplayRotation(encodedW: Int, encodedH: Int, rotation: Int): Bitmap {
        if (rotation % 180 == 0 || encodedW == encodedH || encodedW <= 0 || encodedH <= 0) return this
        val displayLandscape = encodedH > encodedW // swapped by the 90/270° rotation
        val frameLandscape = width > height
        if (frameLandscape == displayLandscape || width == height) return this
        return Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    }

    private fun imageThumbnail(key: ThumbnailKey): Bitmap? {
        val resolver = context.contentResolver
        val uri = Uri.parse(key.uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        // Height of the image as displayed, after EXIF rotation.
        val shownHeight = if (key.rotationDegrees % 180 == 0) bounds.outHeight else bounds.outWidth
        var sample = 1
        while (shownHeight / (sample * 2) >= key.heightPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val rotated = if (key.rotationDegrees == 0) {
            decoded
        } else {
            Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(key.rotationDegrees.toFloat()) }, true,
            )
        }
        return rotated.scaledToHeight(key.heightPx)
    }

    private fun Bitmap.scaledToHeight(heightPx: Int): Bitmap {
        if (height <= heightPx) return this
        val width = (width * heightPx.toFloat() / height).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, width, heightPx, true)
    }

    private fun ThumbnailKey.quantized(): ThumbnailKey = when (type) {
        ClipType.VIDEO -> if (exact) this else copy(sourceMs = sourceMs / TIME_QUANTUM_MS * TIME_QUANTUM_MS)
        ClipType.IMAGE -> copy(sourceMs = 0L)
    }

    private companion object {
        const val MAX_OPEN_RETRIEVERS = 3
        const val TIME_QUANTUM_MS = 250L

        fun cacheSizeKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    }
}
