package com.savie.precut.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.data.ProjectRepository
import com.savie.precut.data.ProjectSummary
import com.savie.precut.data.UnusedMediaCleaner
import com.savie.precut.media.MediaImporter
import com.savie.precut.model.Project
import com.savie.precut.model.clipFromMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

data class HomeUiState(
    val isImporting: Boolean = false,
    /** Drafts, most recent first; null while loading. */
    val projects: List<ProjectSummary>? = null,
)

sealed interface HomeEvent {
    data class OpenEditor(val projectId: String) : HomeEvent
    data class SomeFilesSkipped(val count: Int) : HomeEvent
    data object NothingImported : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val importer: MediaImporter,
    private val repository: ProjectRepository,
    private val clock: Clock,
    cleaner: UnusedMediaCleaner,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeProjects().collect { projects -> _uiState.update { it.copy(projects = projects) } }
        }
        // No editor is open on this screen, so unused recordings can't be needed for undo.
        viewModelScope.launch { cleaner.sweep() }
    }

    fun onRename(projectId: String, name: String) {
        viewModelScope.launch { repository.rename(projectId, name.trim()) }
    }

    fun onDuplicate(projectId: String) {
        viewModelScope.launch { repository.duplicate(projectId, newId(), clock.millis()) }
    }

    fun onDelete(projectId: String) {
        viewModelScope.launch { repository.delete(projectId) }
    }

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    fun onMediaPicked(uris: List<String>) {
        if (uris.isEmpty() || _uiState.value.isImporting) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val result = importer.import(uris)
                if (result.imported.isEmpty()) {
                    _events.send(HomeEvent.NothingImported)
                    return@launch
                }
                val now = clock.millis()
                val project = Project(
                    id = newId(),
                    // Named by the user later; the UI shows a fallback for blank names.
                    name = "",
                    clips = result.imported.map { clipFromMedia(newId(), it.uri, it.info) },
                    createdAt = now,
                    updatedAt = now,
                )
                repository.save(project)
                if (result.failedCount > 0) _events.send(HomeEvent.SomeFilesSkipped(result.failedCount))
                _events.send(HomeEvent.OpenEditor(project.id))
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    private fun newId() = UUID.randomUUID().toString()
}
