package com.savie.precut.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.navigation.toRoute
import com.savie.precut.data.ProjectRepository
import com.savie.precut.media.PreviewController
import com.savie.precut.media.PreviewState
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.ui.navigation.EditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object NotFound : EditorUiState
    data class Ready(val project: Project, val previewClip: Clip?) : EditorUiState
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    private val previewController: PreviewController,
) : ViewModel() {

    private val projectId = savedStateHandle.toRoute<EditorRoute>().projectId

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
            // Phase 1 previews only the first clip; Phase 3 previews the whole composition.
            val firstClip = project.clips.firstOrNull()
            if (firstClip?.type == ClipType.VIDEO) previewController.setMedia(firstClip.uri)
            _uiState.value = EditorUiState.Ready(project, firstClip)
        }
    }

    fun onPlayPause() = previewController.togglePlayPause()

    fun onSeek(positionMs: Long) = previewController.seekTo(positionMs)

    fun onScreenStopped() = previewController.pause()

    override fun onCleared() {
        previewController.release()
    }
}
