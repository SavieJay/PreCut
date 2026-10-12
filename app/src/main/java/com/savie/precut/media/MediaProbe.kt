package com.savie.precut.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

sealed interface ProbeResult {
    data class Success(val info: MediaInfo) : ProbeResult
    data object Unsupported : ProbeResult
    data class Failed(val cause: Throwable) : ProbeResult
}

interface MediaProbe {
    suspend fun probe(uri: String): ProbeResult
}

class AndroidMediaProbe @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MediaProbe {

    override suspend fun probe(uri: String): ProbeResult = withContext(ioDispatcher) {
        val parsed = Uri.parse(uri)
        val mimeType = context.contentResolver.getType(parsed).orEmpty()
        try {
            when {
                mimeType.startsWith("image/") -> probeImage(parsed)
                mimeType.startsWith("video/") -> probeVideo(parsed)
                else -> ProbeResult.Unsupported
            }
        } catch (e: IOException) {
            ProbeResult.Failed(e)
        } catch (e: RuntimeException) {
            // SecurityException for revoked access; MediaMetadataRetriever throws
            // IllegalArgumentException/RuntimeException on unreadable input.
            ProbeResult.Failed(e)
        }
    }

    private fun probeVideo(uri: Uri): ProbeResult {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun int(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (duration <= 0L) return ProbeResult.Unsupported
            return ProbeResult.Success(
                MediaInfo(
                    type = ClipType.VIDEO,
                    durationMs = duration,
                    width = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                    height = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                    rotationDegrees = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                ),
            )
        } finally {
            retriever.release()
        }
    }

    private fun probeImage(uri: Uri): ProbeResult {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return ProbeResult.Failed(IOException("Cannot open $uri"))
        if (options.outWidth <= 0 || options.outHeight <= 0) return ProbeResult.Unsupported
        val rotation = context.contentResolver.openInputStream(uri)?.use {
            exifRotation(ExifInterface(it))
        } ?: 0
        return ProbeResult.Success(
            MediaInfo(
                type = ClipType.IMAGE,
                durationMs = 0L,
                width = options.outWidth,
                height = options.outHeight,
                rotationDegrees = rotation,
            ),
        )
    }

    private fun exifRotation(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
}
