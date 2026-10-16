package com.savie.precut.export

import com.savie.precut.data.AppStorage
import com.savie.precut.data.ProjectRepository
import com.savie.precut.di.ApplicationScope
import com.savie.precut.media.ExportState
import com.savie.precut.media.MediaSaver
import com.savie.precut.media.SavedMedia
import com.savie.precut.media.VideoExporter
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.export.estimatedBytes
import com.savie.precut.model.outputSize
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A video, or only its sound (EX-01 "Audio only"). */
enum class ExportKind { VIDEO, AUDIO }

enum class ExportFailure { PROJECT_NOT_FOUND, PERMISSION_DENIED, EXPORT_FAILED, SAVE_FAILED, ENCODER, LOW_STORAGE }

/** What was asked for: the project and how to export it. */
data class ExportJob(
    val projectId: String,
    val name: String,
    val durationMs: Long,
    val kind: ExportKind,
    val settings: ExportSettings,
    /** For the untitled-project fallback name. */
    val createdAt: Long = 0L,
)

/** The app's one export, shared by the export screen and the export notification. */
sealed interface ExportJobState {
    data object Idle : ExportJobState
    data class Running(val job: ExportJob, val percent: Int?) : ExportJobState
    data class Saving(val job: ExportJob) : ExportJobState
    /** [uri] is null when the file was saved but can't be opened or shared directly. */
    data class Done(val job: ExportJob, val uri: String?, val sizeBytes: Long) : ExportJobState
    /** [neededBytes]/[freeBytes] are set for [ExportFailure.LOW_STORAGE]. */
    data class Failed(
        val job: ExportJob?,
        val reason: ExportFailure,
        val detail: String? = null,
        val neededBytes: Long = 0L,
        val freeBytes: Long = 0L,
    ) : ExportJobState
}

/** Starts the foreground service that keeps an export alive in the background. */
fun interface ExportServiceStarter {
    fun start()
}

/**
 * Runs exports outside any screen (Phase 12), so they continue while the app is in the background:
 * checks storage, exports, saves to the gallery, and publishes progress for the export screen and
 * [ExportService]'s notification. One export at a time.
 */
@Singleton
class ExportController @Inject constructor(
    private val repository: ProjectRepository,
    private val exporter: VideoExporter,
    private val saver: MediaSaver,
    private val storage: AppStorage,
    private val clock: Clock,
    private val serviceStarter: ExportServiceStarter,
    @ApplicationScope appScope: CoroutineScope,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<ExportJobState>(ExportJobState.Idle)
    val state: StateFlow<ExportJobState> = _state.asStateFlow()

    private var running: Job? = null

    val isBusy: Boolean get() = running?.isActive == true

    /** Starts an export; false when one is already running. */
    fun start(projectId: String, settings: ExportSettings, kind: ExportKind): Boolean {
        if (isBusy) return false
        running = scope.launch {
            val project = repository.load(projectId)
            if (project == null) {
                _state.value = ExportJobState.Failed(null, ExportFailure.PROJECT_NOT_FOUND)
                return@launch
            }
            val durationMs = project.totalDurationMs()
            val job = ExportJob(projectId, project.name, durationMs, kind, settings, project.createdAt)
            // The file is written to the cache, then copied to the gallery.
            val needed = settings.estimatedBytes(project.outputSize(settings.resolution.shortSidePx), durationMs, kind == ExportKind.AUDIO) * 2 + STORAGE_MARGIN_BYTES
            val free = storage.freeBytes()
            if (free < needed) {
                _state.value = ExportJobState.Failed(job, ExportFailure.LOW_STORAGE, neededBytes = needed, freeBytes = free)
                return@launch
            }
            _state.value = ExportJobState.Running(job, percent = null)
            serviceStarter.start()
            exporter.export(project, settings, audioOnly = kind == ExportKind.AUDIO).collect { exportState ->
                when (exportState) {
                    is ExportState.Running -> _state.value = ExportJobState.Running(job, exportState.percent)
                    is ExportState.Failed -> _state.value = ExportJobState.Failed(
                        job,
                        if (exportState.encoder) ExportFailure.ENCODER else ExportFailure.EXPORT_FAILED,
                        exportState.cause.message,
                    )
                    is ExportState.Success -> {
                        _state.value = ExportJobState.Saving(job)
                        val size = exportState.file.length()
                        val media = if (kind == ExportKind.AUDIO) SavedMedia.AUDIO else SavedMedia.VIDEO
                        _state.value = saver.save(exportState.file, displayName(media), media).fold(
                            onSuccess = { ExportJobState.Done(job, it, size) },
                            onFailure = { ExportJobState.Failed(job, ExportFailure.SAVE_FAILED, it.message) },
                        )
                    }
                }
            }
        }
        return true
    }

    /** Android 8–9 without storage permission: nothing can be saved. */
    fun onPermissionDenied() {
        _state.value = ExportJobState.Failed(null, ExportFailure.PERMISSION_DENIED)
    }

    fun cancel() {
        running?.cancel()
        running = null
        _state.value = ExportJobState.Idle
    }

    /** Forgets a finished or failed export once it's been seen. */
    fun dismiss() {
        if (!isBusy) _state.value = ExportJobState.Idle
    }

    private fun displayName(media: SavedMedia): String =
        "PreCut_" + FILE_TIME.format(clock.instant().atZone(ZoneId.systemDefault())) + "." + media.extension

    private companion object {
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)
        const val STORAGE_MARGIN_BYTES = 50L * 1024 * 1024
    }
}
