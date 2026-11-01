package com.savie.precut.media.broll

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.ClipType
import com.savie.precut.model.broll.GalleryItem
import com.savie.precut.model.broll.topLabels
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart B-roll (Phase 26B): looks at the newest photos and videos the app may read (all of them,
 * or the ones the user picked on Android 14's partial access) and keeps what the classifier saw in
 * each, on the phone only (`files/broll/index.json`). Items already looked at are reused while
 * unchanged.
 */
@Singleton
class GalleryIndexer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile get() = File(File(context.filesDir, "broll").apply { mkdirs() }, "index.json")

    /** The classifier's class names, in its order. */
    val labelNames: List<String> by lazy { context.assets.open(ImageClassifier.LABELS_ASSET).bufferedReader().readLines() }

    /** Indexes the newest [limit] items; [onProgress] gets (done, total). Cancellable between items. */
    suspend fun index(limit: Int = LIMIT, onProgress: (Int, Int) -> Unit): List<GalleryItem> = withContext(io) {
        val cached = readCache().associateBy { it.id to it.modifiedSec }
        val found = query(limit)
        val classifier = try {
            ImageClassifier.create(context)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "The classifier can't run on this phone", e)
            return@withContext emptyList()
        } catch (e: LinkageError) {
            Log.w(TAG, "LiteRT isn't available on this phone", e)
            return@withContext emptyList()
        }
        val items = classifier.use {
            found.mapIndexedNotNull { index, item ->
                currentCoroutineContext().ensureActive()
                onProgress(index, found.size)
                cached[item.id to item.modifiedSec] ?: thumbnail(item)?.let { picture ->
                    val (labels, scores) = topLabels(classifier.classify(picture))
                    picture.recycle()
                    item.copy(labels = labels, scores = scores)
                }
            }
        }
        writeCache(items)
        items
    }

    /** Photos and videos, newest first, without labels yet. */
    private fun query(limit: Int): List<GalleryItem> {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT, "duration", "orientation",
        )
        val args = Bundle().apply {
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
            )
            putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED))
            putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val items = mutableListOf<GalleryItem>()
        try {
            context.contentResolver.query(uri, projection, args, null)?.use { c ->
                val duration = c.getColumnIndex("duration")
                val orientation = c.getColumnIndex("orientation")
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    items += GalleryItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id).toString(),
                        type = if (video) ClipType.VIDEO else ClipType.IMAGE,
                        durationMs = if (duration >= 0) c.getLong(duration) else 0L,
                        width = c.getInt(3),
                        height = c.getInt(4),
                        rotationDegrees = if (orientation >= 0) c.getInt(orientation) else 0,
                        modifiedSec = c.getLong(2),
                        labels = emptyList(),
                        scores = emptyList(),
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to the gallery", e)
        }
        return items
    }

    /** A small picture of [item]: the system's thumbnail, or a decoded one before Android 10. */
    private fun thumbnail(item: GalleryItem): Bitmap? {
        val uri = item.uri.toUri()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(THUMB, THUMB), null)
            } else if (item.type == ClipType.VIDEO) {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(context, uri)
                        getFrameAtTime(0)
                    } finally {
                        release()
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val sample = (minOf(bounds.outWidth, bounds.outHeight) / THUMB).coerceAtLeast(1)
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            }
        } catch (e: IOException) {
            Log.w(TAG, "No thumbnail for ${item.id}", e)
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "No thumbnail for ${item.id}", e)
            null
        }
    }

    private fun readCache(): List<GalleryItem> = try {
        if (cacheFile.exists()) json.decodeFromString(ListSerializer(GalleryItem.serializer()), cacheFile.readText()) else emptyList()
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    } catch (_: IOException) {
        emptyList()
    }

    private fun writeCache(items: List<GalleryItem>) {
        try {
            cacheFile.writeText(json.encodeToString(ListSerializer(GalleryItem.serializer()), items))
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't keep the gallery index", e)
        }
    }

    private companion object {
        const val TAG = "GalleryIndexer"
        const val LIMIT = 400
        const val THUMB = 256
    }
}
