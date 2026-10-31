package com.savie.precut.media.cutout

import android.opengl.GLES20
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A clip's matte as a GL texture (one luminance channel, rows top-down) on the effects thread:
 * [showAt] brings in the picture for a matte time, unpacking only when it changes.
 */
@OptIn(UnstableApi::class)
internal class MatteFrames private constructor(private val reader: MatteFile.Reader) {
    private val pixels = ByteArray(reader.width * reader.height)
    private val upload: ByteBuffer = ByteBuffer.allocateDirect(pixels.size).order(ByteOrder.nativeOrder())
    private var shown = -1
    private var failed = false

    /** The matte texture; valid after [showAt] returned true. */
    var texture = NO_TEXTURE
        private set

    /** Brings the matte picture nearest [matteUs] into [texture]; false when there's none to show. */
    fun showAt(matteUs: Long): Boolean {
        if (failed) return false
        val index = reader.indexAt(matteUs, toleranceUs = HALF_FRAME_US)
        if (index == shown) return true
        return try {
            reader.read(index, pixels)
            upload.clear()
            upload.put(pixels)
            upload.flip()
            if (texture == NO_TEXTURE) texture = GlUtil.generateTexture()
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GlUtil.bindTexture(GLES20.GL_TEXTURE_2D, texture, GLES20.GL_LINEAR)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, reader.width, reader.height, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, upload)
            GlUtil.checkGlError()
            shown = index
            true
        } catch (e: IOException) {
            // A damaged matte shows the clip uncut rather than failing playback or export.
            Log.w(TAG, "Matte unreadable", e)
            failed = true
            false
        } catch (e: GlUtil.GlException) {
            Log.w(TAG, "Matte upload failed", e)
            failed = true
            false
        }
    }

    fun release() {
        reader.close()
        if (texture != NO_TEXTURE) {
            try {
                GlUtil.deleteTexture(texture)
            } catch (_: GlUtil.GlException) {
                // The context is going away anyway.
            }
            texture = NO_TEXTURE
        }
    }

    companion object {
        private const val TAG = "MatteFrames"
        private const val NO_TEXTURE = -1
        /** Half a frame at 30 fps: the nearest picture, not only the one before. */
        private const val HALF_FRAME_US = 16_700L

        /** The matte at [uri] (a file in app storage); null when it's missing or damaged. */
        fun open(uri: String): MatteFrames? {
            val path = uri.toUri().path ?: return null
            return MatteFile.Reader.open(File(path))?.let(::MatteFrames)
        }
    }
}
