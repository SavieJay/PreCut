package com.savie.precut.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.frame.FrameGeometry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/** A saved freeze frame: a JPEG in app storage and its size. */
data class FrozenFrame(val uri: String, val width: Int, val height: Int)

/** Saves the exact frame of a video clip as a photo for Freeze frame (T-18). */
class FreezeFrames @Inject constructor(
    @ApplicationContext private val context: Context,
    private val thumbnails: ThumbnailLoader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The frame of [clip]'s original media at [sourceMs], at full height; null if it can't be read or saved. */
    suspend fun freeze(clip: Clip, sourceMs: Long): FrozenFrame? {
        if (clip.type != ClipType.VIDEO) return null
        val (_, height) = FrameGeometry.uprightSize(clip.sourceWidth, clip.sourceHeight, clip.sourceRotationDegrees, userRotation = 0)
        val key = ThumbnailKey(clip.uri, clip.type, sourceMs, height.takeIf { it > 0 } ?: FALLBACK_HEIGHT_PX, exact = true)
        val frame = thumbnails.load(key, cache = false) ?: return null
        return withContext(ioDispatcher) {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            try {
                file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                FrozenFrame(Uri.fromFile(file).toString(), frame.width, frame.height)
            } catch (_: IOException) {
                file.delete()
                null
            }
        }
    }

    private companion object {
        const val DIR = "freezes"
        const val JPEG_QUALITY = 95
        const val FALLBACK_HEIGHT_PX = 1080
    }
}
