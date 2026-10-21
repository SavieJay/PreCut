package com.savie.precut.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.data.AppSettings
import com.savie.precut.data.ProjectRepository
import com.savie.precut.data.ProjectSummary
import com.savie.precut.data.ShareInbox
import com.savie.precut.data.UnusedMediaCleaner
import com.savie.precut.media.Capture
import com.savie.precut.media.CaptureStore
import com.savie.precut.media.ImportedMedia
import com.savie.precut.media.MediaImporter
import com.savie.precut.media.SharedMediaStore
import com.savie.precut.media.audio.BeatDetector
import com.savie.precut.model.AudioClip
import com.savie.precut.model.Project
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.edit.autoCutProject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
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
    /** The screen should launch the camera app writing to [cameraUri]. */
    data class LaunchCamera(val cameraUri: String, val video: Boolean) : HomeEvent
    data class SomeFilesSkipped(val count: Int) : HomeEvent
    data object NothingImported : HomeEvent
    /** AutoCut's song couldn't be read. */
    data object AutoCutMusicUnreadable : HomeEvent
    /** AutoCut found no steady beat: the clips were kept in order, uncut. */
    data object AutoCutNoBeats : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val importer: MediaImporter,
    private val repository: ProjectRepository,
    private val clock: Clock,
    cleaner: UnusedMediaCleaner,
    private val captures: CaptureStore,
    private val shareInbox: ShareInbox,
    private val sharedMedia: SharedMediaStore,
    private val settings: AppSettings,
    private val beats: BeatDetector,
) : ViewModel() {

    /** The camera capture in progress, until the camera app returns. */
    private var pendingCapture: Capture? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Declared before init: a share waiting at construction may emit right away.
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    // No editor is open on this screen, so unused recordings can't be needed for undo. New media
    // is copied in only after it finishes, or the sweep would delete it before its project is saved.
    private val sweep = viewModelScope.launch { cleaner.sweep() }

    init {
        viewModelScope.launch {
            repository.observeProjects().collect { projects -> _uiState.update { it.copy(projects = projects) } }
        }
        viewModelScope.launch {
            shareInbox.pending.filter { it.isNotEmpty() }.collect { onShared(shareInbox.take()) }
        }
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

    fun onMediaPicked(uris: List<String>) = createProject(uris)

    fun onCameraRequested(video: Boolean) {
        val capture = captures.newCapture(video)
        pendingCapture = capture
        viewModelScope.launch { _events.send(HomeEvent.LaunchCamera(capture.cameraUri, video)) }
    }

    fun onCaptureResult(success: Boolean) {
        val capture = pendingCapture ?: return
        pendingCapture = null
        if (success) createProject(listOf(capture.fileUri)) else captures.discard(capture)
    }

    /**
     * AutoCut: a new project of [media] in the order picked, with [music] from the start and every
     * clip cut to its beat (every [every]th beat, videos taken from their middle). It's a normal,
     * editable project; without a steady beat the clips stay uncut.
     */
    fun onAutoCut(media: List<String>, music: String, every: Int) {
        if (media.isEmpty() || _uiState.value.isImporting) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                sweep.join()
                val result = importer.import(media)
                if (result.imported.isEmpty()) {
                    _events.send(HomeEvent.NothingImported)
                    return@launch
                }
                val song = importer.importAudio(music)
                if (song == null) {
                    _events.send(HomeEvent.AutoCutMusicUnreadable)
                    return@launch
                }
                val found = beats.detect(song.uri)?.every(every).orEmpty()
                val track = AudioClip(id = newId(), uri = song.uri, sourceDurationMs = song.durationMs, trimEndMs = song.durationMs, displayName = song.displayName)
                val project = autoCutProject(newProject(result.imported), track, found)
                repository.save(project)
                if (result.failedCount > 0) _events.send(HomeEvent.SomeFilesSkipped(result.failedCount))
                if (found.isEmpty()) _events.send(HomeEvent.AutoCutNoBeats)
                _events.send(HomeEvent.OpenEditor(project.id))
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    /** Shared Uris only have temporary grants, so they're copied into app storage first. */
    private fun onShared(uris: List<String>) = createProject(uris, copyFirst = true)

    private fun createProject(uris: List<String>, copyFirst: Boolean = false) {
        if (uris.isEmpty() || _uiState.value.isImporting) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                sweep.join()
                val local = if (copyFirst) sharedMedia.copyIn(uris) else uris
                val result = importer.import(local).let { it.copy(failedCount = it.failedCount + uris.size - local.size) }
                if (result.imported.isEmpty()) {
                    _events.send(HomeEvent.NothingImported)
                    return@launch
                }
                val project = newProject(result.imported)
                repository.save(project)
                if (result.failedCount > 0) _events.send(HomeEvent.SomeFilesSkipped(result.failedCount))
                _events.send(HomeEvent.OpenEditor(project.id))
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    /** A new project of [imported] media in order, with the Settings defaults. */
    private fun newProject(imported: List<ImportedMedia>): Project {
        val now = clock.millis()
        val defaults = settings.values.value
        return Project(
            id = newId(),
            // Named by the user later; the UI shows a fallback for blank names.
            name = "",
            clips = imported.map { clipFromMedia(newId(), it.uri, it.info, defaults.photoDurationMs) },
            aspectRatio = defaults.aspectRatio,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun newId() = UUID.randomUUID().toString()
}
