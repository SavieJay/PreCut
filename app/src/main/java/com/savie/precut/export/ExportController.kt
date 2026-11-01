package com.savie.precut.export

import com.savie.precut.data.AppStorage
import com.savie.precut.data.ProjectRepository
import com.savie.precut.di.ApplicationScope
import com.savie.precut.media.ExportState
import com.savie.precut.media.MediaSaver
import com.savie.precut.media.SavedMedia
import com.savie.precut.media.VideoExporter
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.Project
import com.savie.precut.model.export.ExportBitrate
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.export.estimatedBytes
import com.savie.precut.model.export.withEndCard
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

/** A video, only its sound (EX-01 "Audio only"), or a looping GIF; each saved as its [media]. */
enum class ExportKind(val media: SavedMedia) { VIDEO(SavedMedia.VIDEO), AUDIO(SavedMedia.AUDIO), GIF(SavedMedia.GIF) }

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
    /** Multi-format export (Phase 26B): this video's place in the set, and its shape. */
    val part: Int = 0,
    val parts: Int = 1,
    val format: AspectRatio? = null,
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
    /** The "Made with PreCut" end card, when chosen (Phase 28). */
    private val endCards: EndCards = EndCards.NONE,
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
            exportOne(project, ExportJob(projectId, project.name, project.totalDurationMs(), kind, settings, project.createdAt))
        }
        return true
    }

    /**
     * Multi-format export (Phase 26B): [variants] of project [projectId] (the same edit in other
     * shapes, already reframed), one after another, each saved to the gallery; stops at a failure.
     */
    fun startVariants(projectId: String, variants: List<Project>, settings: ExportSettings): Boolean {
        if (isBusy || variants.isEmpty()) return false
        running = scope.launch {
            variants.forEachIndexed { index, variant ->
                val job = ExportJob(
                    projectId, variant.name, variant.totalDurationMs(), ExportKind.VIDEO, settings, variant.createdAt,
                    part = index, parts = variants.size, format = variant.aspectRatio,
                )
                if (!exportOne(variant, job)) return@launch
            }
        }
        return true
    }

    /** Exports [project] as [job] and saves it; false when it failed. */
    private suspend fun exportOne(edit: Project, job: ExportJob): Boolean {
        val settings = job.settings
        val kind = job.kind
        // The end card only on videos, and only when chosen.
        val project = if (settings.endCard && kind == ExportKind.VIDEO) {
            val size = edit.outputSize()
            endCards.make(size.width, size.height)?.let { (uri, info) -> edit.withEndCard(uri, info) } ?: edit
        } else {
            edit
        }
        // The file is written to the cache, then copied to the gallery.
        val needed = neededBytes(project, settings, kind, job.durationMs) + STORAGE_MARGIN_BYTES
        val free = storage.freeBytes()
        if (free < needed) {
            _state.value = ExportJobState.Failed(job, ExportFailure.LOW_STORAGE, neededBytes = needed, freeBytes = free)
            return false
        }
        _state.value = ExportJobState.Running(job, percent = null)
        serviceStarter.start()
        val export = if (kind == ExportKind.GIF) exporter.exportGif(project, settings.gif) else exporter.export(project, settings, audioOnly = kind == ExportKind.AUDIO)
        export.collect { exportState ->
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
                    val media = kind.media
                    _state.value = saver.save(exportState.file, displayName(media), media).fold(
                        onSuccess = { ExportJobState.Done(job, it, size) },
                        onFailure = { ExportJobState.Failed(job, ExportFailure.SAVE_FAILED, it.message) },
                    )
                }
            }
        }
        return _state.value is ExportJobState.Done
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

    /** Room for the temporary file and its gallery copy (a GIF also needs its temporary MP4). */
    private fun neededBytes(project: Project, settings: ExportSettings, kind: ExportKind, durationMs: Long): Long {
        if (kind == ExportKind.GIF) {
            val size = project.outputSize(settings.gif.size.shortSidePx)
            val video = ExportSettings(frameRate = settings.gif.frameRate, bitrate = ExportBitrate.HIGHER).estimatedBytes(size, durationMs)
            return video + settings.gif.estimatedBytes(size, durationMs) * 2
        }
        return settings.estimatedBytes(project.outputSize(settings.resolution.shortSidePx), durationMs, kind == ExportKind.AUDIO) * 2
    }

    private fun displayName(media: SavedMedia): String =
        "PreCut_" + FILE_TIME.format(clock.instant().atZone(ZoneId.systemDefault())) + "." + media.extension

    private companion object {
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)
        const val STORAGE_MARGIN_BYTES = 50L * 1024 * 1024
    }
}
