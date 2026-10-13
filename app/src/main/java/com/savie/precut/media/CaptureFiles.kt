package com.savie.precut.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * A file the camera app writes into: [cameraUri] is handed to the camera (FileProvider grant),
 * [fileUri] is what the project stores.
 */
data class Capture(val fileUri: String, val cameraUri: String)

interface CaptureStore {
    fun newCapture(video: Boolean): Capture

    /** Deletes a capture the camera didn't fill (cancelled). */
    fun discard(capture: Capture)
}

class CaptureFiles @Inject constructor(@ApplicationContext private val context: Context) : CaptureStore {

    override fun newCapture(video: Boolean): Capture {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.${if (video) "mp4" else "jpg"}")
        val cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Capture(Uri.fromFile(file).toString(), cameraUri.toString())
    }

    override fun discard(capture: Capture) {
        Uri.parse(capture.fileUri).path?.let { File(it).delete() }
    }

    private companion object {
        const val DIR = "captures"
    }
}
