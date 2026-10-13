package com.savie.precut.media

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.savie.precut.model.Project
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed interface ExportState {
    /** [percent] is null while Transformer can't estimate progress yet. */
    data class Running(val percent: Int?) : ExportState
    data class Success(val file: File) : ExportState
    data class Failed(val cause: Throwable) : ExportState
}

interface VideoExporter {
    /** Exports [project] to a temporary MP4. Cancelling collection cancels the export. */
    fun export(project: Project): Flow<ExportState>
}

class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compositionBuilder: CompositionBuilder,
    private val proxies: ProxyManager,
) : VideoExporter {

    override fun export(project: Project): Flow<ExportState> {
        val composition = compositionBuilder.build(project)
            ?: return flowOf(ExportState.Failed(IllegalStateException("Project has no clips")))
        return export(composition)
    }

    private fun export(composition: Composition): Flow<ExportState> = callbackFlow {
        val output = File(File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }, "${UUID.randomUUID()}.mp4")
        var finished = false

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    trySend(ExportState.Success(output))
                    close()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    finished = true
                    output.delete()
                    trySend(ExportState.Failed(exportException))
                    close()
                }
            })
            .build()

        trySend(ExportState.Running(percent = null))
        // Background proxying would compete for the encoder; it resumes when the export ends.
        proxies.pause()
        transformer.start(composition, output.absolutePath)

        val progressHolder = ProgressHolder()
        val poller = launch {
            while (isActive) {
                delay(PROGRESS_POLL_MS)
                if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(ExportState.Running(progressHolder.progress))
                }
            }
        }

        awaitClose {
            proxies.resume()
            poller.cancel()
            if (!finished) {
                transformer.cancel()
                output.delete()
            }
        }
    }.flowOn(Dispatchers.Main.immediate) // Transformer must be used from a Looper thread.

    private companion object {
        const val EXPORT_DIR = "exports"
        const val PROGRESS_POLL_MS = 200L
    }
}
