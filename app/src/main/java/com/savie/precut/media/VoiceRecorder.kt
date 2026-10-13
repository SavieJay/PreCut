package com.savie.precut.media

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Records voiceovers from the mic to AAC/M4A files in app storage (kept with the project). Needs
 * RECORD_AUDIO. Not thread-safe; use from the main thread. Always [release] when done.
 */
class VoiceRecorder @Inject constructor(@ApplicationContext private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Starts a new recording; false if the mic or file couldn't be opened. */
    fun start(): Boolean {
        if (recorder != null) return true
        val dir = File(context.filesDir, VOICEOVER_DIR).apply { mkdirs() }
        val output = File(dir, "${UUID.randomUUID()}.m4a")
        val mediaRecorder = newRecorder()
        return try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioEncodingBitRate(128_000)
            mediaRecorder.setOutputFile(output.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            file = output
            true
        } catch (_: IOException) {
            mediaRecorder.release()
            output.delete()
            false
        } catch (_: RuntimeException) {
            // IllegalStateException / mic in use / permission revoked.
            mediaRecorder.release()
            output.delete()
            false
        }
    }

    /** Stops and returns the recording's Uri, or null if nothing usable was recorded. */
    fun stop(): String? {
        val mediaRecorder = recorder ?: return null
        val output = file
        recorder = null
        file = null
        val ok = try {
            mediaRecorder.stop()
            true
        } catch (_: RuntimeException) {
            false // stop() throws when nothing was captured (e.g. stopped instantly)
        } finally {
            mediaRecorder.release()
        }
        if (!ok) output?.delete()
        return if (ok && output != null) Uri.fromFile(output).toString() else null
    }

    /** Abandons any recording in progress. */
    fun release() {
        stop()?.let { Uri.parse(it).path?.let(::File)?.delete() }
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") // The Context constructor only exists from API 31.
            MediaRecorder()
        }

    private companion object {
        const val VOICEOVER_DIR = "voiceovers"
    }
}
