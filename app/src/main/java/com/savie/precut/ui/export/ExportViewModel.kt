package com.savie.precut.ui.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.data.ProjectRepository
import com.savie.precut.media.ExportState
import com.savie.precut.media.MediaSaver
import com.savie.precut.media.VideoExporter
import com.savie.precut.model.totalDurationMs
import com.savie.precut.ui.navigation.requireProjectId
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ExportFailure { PROJECT_NOT_FOUND, PERMISSION_DENIED, EXPORT_FAILED, SAVE_FAILED }

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val percent: Int?) : ExportUiState
    data object Saving : ExportUiState
    /** [uri] is null when the video was saved but can't be opened or shared directly. */
    data class Done(val uri: String?) : ExportUiState
    data class Failed(val reason: ExportFailure, val detail: String? = null) : ExportUiState
}

/** What the export screens show about the project: its name, length and draft cover. */
data class ExportInfo(val name: String, val createdAt: Long, val durationMs: Long, val coverPath: String?)

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    private val exporter: VideoExporter,
    private val saver: MediaSaver,
    private val clock: Clock,
) : ViewModel() {

    private val projectId = savedStateHandle.requireProjectId()

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private val _info = MutableStateFlow<ExportInfo?>(null)
    val info: StateFlow<ExportInfo?> = _info.asStateFlow()

    private var job: Job? = null

    /** Starts the export unless one is running or already finished. Retries after a failure. */
    fun start() {
        val state = _uiState.value
        if (job?.isActive == true || state is ExportUiState.Done) return
        job = viewModelScope.launch {
            val project = repository.load(projectId)
            if (project == null) {
                _uiState.value = ExportUiState.Failed(ExportFailure.PROJECT_NOT_FOUND)
                return@launch
            }
            val cover = repository.observeProjects().first().firstOrNull { it.id == projectId }?.coverPath
            _info.value = ExportInfo(project.name, project.createdAt, project.totalDurationMs(), cover)
            _uiState.value = ExportUiState.Exporting(percent = null)
            exporter.export(project).collect { exportState ->
                when (exportState) {
                    is ExportState.Running -> _uiState.value = ExportUiState.Exporting(exportState.percent)
                    is ExportState.Failed -> _uiState.value =
                        ExportUiState.Failed(ExportFailure.EXPORT_FAILED, exportState.cause.message)
                    is ExportState.Success -> {
                        _uiState.value = ExportUiState.Saving
                        _uiState.value = saver.saveVideo(exportState.file, displayName()).fold(
                            onSuccess = { ExportUiState.Done(it) },
                            onFailure = { ExportUiState.Failed(ExportFailure.SAVE_FAILED, it.message) },
                        )
                    }
                }
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.value = ExportUiState.Failed(ExportFailure.PERMISSION_DENIED)
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private fun displayName(): String =
        "PreCut_" + FILE_TIME.format(clock.instant().atZone(ZoneId.systemDefault())) + ".mp4"

    private companion object {
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)
    }
}
