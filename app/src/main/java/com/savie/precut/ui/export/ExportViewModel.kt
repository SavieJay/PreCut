package com.savie.precut.ui.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.data.AppStorage
import com.savie.precut.data.ProjectRepository
import com.savie.precut.export.ExportController
import com.savie.precut.export.ExportFailure
import com.savie.precut.export.ExportJobState
import com.savie.precut.export.ExportKind
import com.savie.precut.model.export.ExportResolution
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.totalDurationMs
import com.savie.precut.ui.navigation.EXPORT_KIND_ARG
import com.savie.precut.ui.navigation.EXPORT_SETTINGS_ARG
import com.savie.precut.ui.navigation.requireProjectId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val percent: Int?) : ExportUiState
    data object Saving : ExportUiState
    /** [uri] is null when the file was saved but can't be opened or shared directly. */
    data class Done(val uri: String?, val audio: Boolean = false) : ExportUiState
    data class Failed(
        val reason: ExportFailure,
        val detail: String? = null,
        val neededBytes: Long = 0L,
        val freeBytes: Long = 0L,
    ) : ExportUiState
}

/** What the export screens show about the project: its name, length, draft cover and settings. */
data class ExportInfo(
    val name: String,
    val createdAt: Long,
    val durationMs: Long,
    val coverPath: String?,
    val settings: ExportSettings = ExportSettings(),
    val audio: Boolean = false,
)

/**
 * The export screen (EX-02, EX-04, EX-05) over [ExportController], which runs the export itself
 * so it outlives this screen.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    private val controller: ExportController,
    private val storage: AppStorage,
) : ViewModel() {

    private val projectId = savedStateHandle.requireProjectId()
    private var settings: ExportSettings =
        savedStateHandle.get<String>(EXPORT_SETTINGS_ARG)?.takeIf { it.isNotBlank() }?.let { runCatching { Json.decodeFromString<ExportSettings>(it) }.getOrNull() }
            ?: ExportSettings()
    private val kind: ExportKind = savedStateHandle.get<String>(EXPORT_KIND_ARG)?.let { name -> ExportKind.entries.firstOrNull { it.name == name } } ?: ExportKind.VIDEO

    val uiState: StateFlow<ExportUiState> = controller.state
        .map { state -> state.toUi() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, controller.state.value.toUi())

    private val _info = MutableStateFlow<ExportInfo?>(null)
    val info: StateFlow<ExportInfo?> = _info.asStateFlow()

    private val _cacheBytes = MutableStateFlow(0L)
    /** For the not-enough-storage screen's "Clearing the preview cache frees …". */
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    init {
        viewModelScope.launch { loadInfo() }
    }

    /** Starts the export unless one is running or this one already finished. Retries after a failure. */
    fun start() {
        if (controller.isBusy || uiState.value is ExportUiState.Done) return
        controller.start(projectId, settings, kind)
        viewModelScope.launch { loadInfo() }
    }

    /** EX-05: retry one resolution lower (4K → 2K → 1080p…), at most 30 fps. */
    fun startLower() {
        val lower = ExportResolution.entries.getOrNull(settings.resolution.ordinal - 1) ?: settings.resolution
        settings = settings.copy(resolution = lower, frameRate = minOf(settings.frameRate, 30))
        start()
    }

    /** EX-05b: frees the preview cache, then tries again. */
    fun clearCacheAndRetry() {
        viewModelScope.launch {
            storage.clearCache()
            start()
        }
    }

    fun onPermissionDenied() = controller.onPermissionDenied()

    fun cancel() = controller.cancel()

    /** Leaving after the export finished or failed. */
    fun dismiss() = controller.dismiss()

    /** The resolution [startLower] would use. */
    fun lowerResolution(): ExportResolution = ExportResolution.entries.getOrNull(settings.resolution.ordinal - 1) ?: settings.resolution

    fun currentSettings(): ExportSettings = settings

    private suspend fun loadInfo() {
        val project = repository.load(projectId) ?: return
        val cover = repository.observeProjects().first().firstOrNull { it.id == projectId }?.coverPath
        _info.value = ExportInfo(project.name, project.createdAt, project.totalDurationMs(), cover, settings, kind == ExportKind.AUDIO)
        _cacheBytes.value = storage.cacheBytes()
    }

    private fun ExportJobState.toUi(): ExportUiState = when (this) {
        // Another project's export isn't this screen's.
        is ExportJobState.Running -> if (job.projectId == projectId) ExportUiState.Exporting(percent) else ExportUiState.Idle
        is ExportJobState.Saving -> if (job.projectId == projectId) ExportUiState.Saving else ExportUiState.Idle
        is ExportJobState.Done -> if (job.projectId == projectId) ExportUiState.Done(uri, job.kind == ExportKind.AUDIO) else ExportUiState.Idle
        is ExportJobState.Failed -> if (job == null || job.projectId == projectId) ExportUiState.Failed(reason, detail, neededBytes, freeBytes) else ExportUiState.Idle
        ExportJobState.Idle -> ExportUiState.Idle
    }
}
