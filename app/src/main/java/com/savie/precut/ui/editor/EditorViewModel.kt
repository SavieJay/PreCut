package com.savie.precut.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.savie.precut.data.ProjectRepository
import com.savie.precut.media.PreviewController
import com.savie.precut.media.PreviewState
import com.savie.precut.model.Project
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.ui.navigation.requireProjectId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

enum class EditorTool { TRIM }

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object NotFound : EditorUiState
    data class Ready(val project: Project, val activeTool: EditorTool? = null) : EditorUiState
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    private val previewController: PreviewController,
    private val clock: Clock,
) : ViewModel() {

    val projectId = savedStateHandle.requireProjectId()

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val previewState: StateFlow<PreviewState> = previewController.state

    /** For attaching the video surface only; control playback through the event methods. */
    val player: Player get() = previewController.player

    init {
        viewModelScope.launch {
            val project = repository.load(projectId)
            if (project == null) {
                _uiState.value = EditorUiState.NotFound
                return@launch
            }
            previewController.setProject(project)
            _uiState.value = EditorUiState.Ready(project)
        }
    }

    fun onPlayPause() = previewController.togglePlayPause()

    fun onSeek(positionMs: Long) = previewController.seekTo(positionMs)

    fun onScreenStopped() = previewController.pause()

    fun onToolSelected(tool: EditorTool) = updateReady { it.copy(activeTool = if (it.activeTool == tool) null else tool) }

    fun onToolClosed() = updateReady { it.copy(activeTool = null) }

    fun onTrim(clipId: String, trimStartMs: Long, trimEndMs: Long) =
        applyEdit(EditAction.TrimClip(clipId, trimStartMs, trimEndMs))

    private fun applyEdit(action: EditAction) {
        val ready = _uiState.value as? EditorUiState.Ready ?: return
        val edited = EditReducer.reduce(ready.project, action)
        if (edited === ready.project) return
        val saved = edited.copy(updatedAt = clock.millis())
        _uiState.value = ready.copy(project = saved)
        previewController.pause()
        previewController.setProject(saved)
        viewModelScope.launch { repository.save(saved) }
    }

    private fun updateReady(transform: (EditorUiState.Ready) -> EditorUiState.Ready) =
        _uiState.update { if (it is EditorUiState.Ready) transform(it) else it }

    override fun onCleared() {
        previewController.release()
    }
}
