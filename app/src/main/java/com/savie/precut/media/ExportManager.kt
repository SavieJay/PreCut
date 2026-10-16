package com.savie.precut.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import com.savie.precut.model.OutputSize
import com.savie.precut.model.export.EXPORT_AUDIO_BITRATE
import com.savie.precut.model.export.ExportCodec
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.export.videoBitrate
import com.savie.precut.model.outputSize
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
    /** [encoder]: the phone couldn't encode these settings (try a lower resolution or H.264). */
    data class Failed(val cause: Throwable, val encoder: Boolean = false) : ExportState
}

interface VideoExporter {
    /**
     * Exports [project] with [settings] to a temporary MP4 (an M4A when [audioOnly]). Cancelling
     * collection cancels the export.
     */
    fun export(project: Project, settings: ExportSettings = ExportSettings(), audioOnly: Boolean = false): Flow<ExportState>
}

// Transformer's encoder settings and error codes are unstable in Media3 1.11.
@OptIn(UnstableApi::class)
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compositionBuilder: CompositionBuilder,
    private val proxies: ProxyManager,
) : VideoExporter {

    override fun export(project: Project, settings: ExportSettings, audioOnly: Boolean): Flow<ExportState> {
        val output = ExportOutput(frameRate = settings.frameRate, keepHdr = settings.keepHdr, audioOnly = audioOnly)
        val composition = compositionBuilder.build(project, settings.resolution.shortSidePx, output = output)
            ?: return flowOf(ExportState.Failed(IllegalStateException("Project has no clips")))
        return export(composition, settings, project.outputSize(settings.resolution.shortSidePx), audioOnly)
    }

    private fun export(composition: Composition, settings: ExportSettings, size: OutputSize, audioOnly: Boolean): Flow<ExportState> = callbackFlow {
        val extension = if (audioOnly) "m4a" else "mp4"
        val output = File(File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }, "${UUID.randomUUID()}.$extension")
        var finished = false

        val encoders = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(settings.videoBitrate(size)).build())
            .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(EXPORT_AUDIO_BITRATE).build())
            .build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(if (settings.codec == ExportCodec.HEVC) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoders)
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
                    trySend(ExportState.Failed(exportException, encoder = exportException.errorCode in ENCODER_ERRORS))
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
        val ENCODER_ERRORS = setOf(
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
            ExportException.ERROR_CODE_ENCODING_FAILED,
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        )
        const val EXPORT_DIR = "exports"
        const val PROGRESS_POLL_MS = 200L
    }
}
