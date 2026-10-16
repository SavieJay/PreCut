package com.savie.precut.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.savie.precut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Where each kind of file is saved: Movies, Music or Pictures, in a PreCut album. The folder
 * names are Android's fixed `Environment.DIRECTORY_*` values, written out so this also loads
 * in unit tests.
 */
enum class SavedMedia(val mime: String, val extension: String, val directory: String) {
    VIDEO("video/mp4", "mp4", "Movies"),
    AUDIO("audio/mp4", "m4a", "Music"),
    IMAGE("image/jpeg", "jpg", "Pictures"),
}

interface MediaSaver {
    /**
     * Copies [file] into the shared PreCut album for [media] and deletes [file]. Returns the new
     * content Uri, or null if the file was saved but the system didn't report a Uri for it.
     */
    suspend fun save(file: File, displayName: String, media: SavedMedia): Result<String?>
}

class MediaStoreSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MediaSaver {

    override suspend fun save(file: File, displayName: String, media: SavedMedia): Result<String?> = try {
        val uri = withContext(ioDispatcher) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(file, displayName, media) else null
        } ?: saveLegacy(file, displayName, media)
        Result.success(uri?.toString())
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: SecurityException) {
        Result.failure(e)
    } finally {
        file.delete()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(file: File, displayName: String, media: SavedMedia): Uri {
        val resolver = context.contentResolver
        val collection = when (media) {
            SavedMedia.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            SavedMedia.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            SavedMedia.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, media.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${media.directory}/$ALBUM")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Cannot open $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /**
     * Android 8–9: no RELATIVE_PATH, so write to the public folder directly (needs
     * WRITE_EXTERNAL_STORAGE) and let the media scanner index it.
     */
    @Suppress("DEPRECATION") // getExternalStoragePublicDirectory is the only option before API 29.
    private suspend fun saveLegacy(file: File, displayName: String, media: SavedMedia): Uri? {
        val target = withContext(ioDispatcher) {
            val dir = File(Environment.getExternalStoragePublicDirectory(media.directory), ALBUM)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create $dir")
            File(dir, displayName).also { file.copyTo(it, overwrite = true) }
        }
        return suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(media.mime)) { _, uri ->
                cont.resume(uri)
            }
        }
    }

    private companion object {
        const val ALBUM = "PreCut"
    }
}
