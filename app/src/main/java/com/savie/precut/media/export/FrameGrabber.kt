package com.savie.precut.media.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.Size
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import com.savie.precut.media.CompositionBuilder
import com.savie.precut.model.Project
import com.savie.precut.model.outputSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * "Save frame" (EX-01): the composed frame at a time, at export resolution. A second, offscreen
 * [CompositionPlayer] renders the same [CompositionBuilder] composition the preview and export
 * use into an [ImageReader], so the photo matches the video exactly.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class) // CompositionPlayer
class FrameGrabber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compositionBuilder: CompositionBuilder,
) {
    /** Null when the project has nothing to show or the frame doesn't arrive in time. */
    suspend fun grab(project: Project, positionMs: Long, shortSidePx: Int): Bitmap? = withContext(Dispatchers.Main) {
        val composition = compositionBuilder.build(project, shortSidePx) ?: return@withContext null
        val size = project.outputSize(shortSidePx)
        val reader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, 2)
        val player = CompositionPlayer.Builder(context).build()
        try {
            val frame = withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    reader.setOnImageAvailableListener({ r ->
                        val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                        val bitmap = image.use {
                            val plane = it.planes[0]
                            // Rows may be padded; copy into a bitmap as wide as the padding, then crop.
                            val padded = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, it.height, Bitmap.Config.ARGB_8888)
                            padded.copyPixelsFromBuffer(plane.buffer)
                            if (padded.width == it.width) padded else Bitmap.createBitmap(padded, 0, 0, it.width, it.height).also { padded.recycle() }
                        }
                        reader.setOnImageAvailableListener(null, null)
                        if (cont.isActive) cont.resume(bitmap)
                    }, Handler(Looper.getMainLooper()))
                    player.setComposition(composition, positionMs.coerceAtLeast(0L))
                    player.setVideoSurface(reader.surface, Size(size.width, size.height))
                    player.prepare()
                }
            }
            frame
        } finally {
            player.release()
            reader.close()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
