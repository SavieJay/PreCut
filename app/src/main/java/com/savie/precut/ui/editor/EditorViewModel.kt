package com.savie.precut.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.savie.precut.data.ProjectRepository
import com.savie.precut.media.MediaImporter
import com.savie.precut.media.PreviewController
import com.savie.precut.media.PreviewState
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.ClipType
import com.savie.precut.model.FilterType
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipAt
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.totalDurationMs
import com.savie.precut.ui.navigation.requireProjectId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** Panels that replace the toolbar while open. */
enum class EditorTool { TRIM, SPEED, FILTER, TEXT, RATIO }

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object NotFound : EditorUiState
    data class Ready(
        val project: Project,
        val selectedClipId: String? = null,
        val selectedTextId: String? = null,
        val activeTool: EditorTool? = null,
        val isImporting: Boolean = false,
    ) : EditorUiState
}

sealed interface EditorEvent {
    data class SomeFilesSkipped(val count: Int) : EditorEvent
    data object NothingImported : EditorEvent
    data object SpeedNotForPhotos : EditorEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    private val previewController: PreviewController,
    private val importer: MediaImporter,
    /** For the timeline's thumbnail tiles. */
    val thumbnails: ThumbnailLoader,
    private val clock: Clock,
) : ViewModel() {

    val projectId = savedStateHandle.requireProjectId()

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    val previewState: StateFlow<PreviewState> = previewController.state

    /** Whether Split would do something at the current playhead. */
    val canSplit: StateFlow<Boolean> = combine(_uiState, previewState) { ui, preview ->
        ui is EditorUiState.Ready && EditReducer.canSplit(ui.project, preview.positionMs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** For attaching the video surface only; control playback through the event methods. */
    val player: Player get() = previewController.player

    /** Latest edited project; the preview rebuilds once edits pause, so rapid edits rebuild once. */
    private val pendingPreview = MutableStateFlow<Project?>(null)

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
        viewModelScope.launch {
            pendingPreview.filterNotNull().debounce(PREVIEW_REBUILD_DEBOUNCE_MS).collect { project ->
                val resumeAt = previewState.value.positionMs.coerceAtMost(project.totalDurationMs())
                previewController.setProject(project, resumeAt)
            }
        }
    }

    // Playback

    fun onPlayPause() = previewController.togglePlayPause()

    fun onScrubStart() = previewController.pause()

    fun onSeek(positionMs: Long) = previewController.seekTo(positionMs)

    fun onScreenStopped() = previewController.pause()

    // Selection and tools

    fun onClipSelected(clipId: String?) {
        dropBlankSelectedText()
        updateReady {
            val clip = it.project.clips.firstOrNull { c -> c.id == clipId }
            // Keep a clip tool open when switching clips, unless it doesn't apply (speed on a photo).
            val keepTool = clip != null && it.activeTool in CLIP_TOOLS &&
                !(it.activeTool == EditorTool.SPEED && clip.type == ClipType.IMAGE)
            it.copy(selectedClipId = clipId, selectedTextId = null, activeTool = if (keepTool) it.activeTool else null)
        }
    }

    fun onTextSelected(textId: String?) {
        dropBlankSelectedText()
        updateReady {
            it.copy(selectedTextId = textId, selectedClipId = null, activeTool = if (textId != null) EditorTool.TEXT else null)
        }
    }

    fun onToolSelected(tool: EditorTool) {
        val ready = _uiState.value as? EditorUiState.Ready ?: return
        if (ready.activeTool == tool) return onToolClosed()
        when (tool) {
            EditorTool.RATIO -> updateReady { it.copy(activeTool = tool) }
            EditorTool.TEXT -> addText(ready)
            EditorTool.TRIM, EditorTool.SPEED, EditorTool.FILTER -> {
                val clipId = targetClipId() ?: return
                val clip = ready.project.clips.first { it.id == clipId }
                if (tool == EditorTool.SPEED && clip.type == ClipType.IMAGE) {
                    viewModelScope.launch { _events.send(EditorEvent.SpeedNotForPhotos) }
                    return
                }
                updateReady { it.copy(selectedClipId = clipId, selectedTextId = null, activeTool = tool) }
            }
        }
    }

    fun onToolClosed() {
        dropBlankSelectedText()
        updateReady { it.copy(activeTool = null, selectedTextId = null) }
    }

    // Edits

    fun onSplit() = applyEdit(EditAction.SplitClip(previewState.value.positionMs, newId()))

    fun onDelete() {
        val clipId = targetClipId() ?: return
        applyEdit(EditAction.DeleteClip(clipId))
        updateReady { it.copy(selectedClipId = null, activeTool = null) }
    }

    fun onDuplicate() {
        val clipId = targetClipId() ?: return
        applyEdit(EditAction.DuplicateClip(clipId, newId()))
    }

    fun onTrim(clipId: String, trimStartMs: Long, trimEndMs: Long) =
        applyEdit(EditAction.TrimClip(clipId, trimStartMs, trimEndMs))

    fun onMove(from: Int, to: Int) = applyEdit(EditAction.MoveClip(from, to))

    fun onRotate() {
        val clipId = targetClipId() ?: return
        applyEdit(EditAction.RotateClip(clipId))
    }

    fun onSpeedChanged(speed: Float) {
        val clipId = (_uiState.value as? EditorUiState.Ready)?.selectedClipId ?: return
        applyEdit(EditAction.SetSpeed(clipId, speed))
    }

    fun onFilterSelected(filter: FilterType) {
        val clipId = (_uiState.value as? EditorUiState.Ready)?.selectedClipId ?: return
        applyEdit(EditAction.SetFilter(clipId, filter))
    }

    fun onAspectRatioSelected(aspectRatio: AspectRatio) = applyEdit(EditAction.SetAspectRatio(aspectRatio))

    fun onTextChanged(overlay: TextOverlay) = applyEdit(EditAction.UpdateText(overlay))

    fun onTextMoved(textId: String, xNorm: Float, yNorm: Float) {
        val overlay = findText(textId) ?: return
        applyEdit(EditAction.UpdateText(overlay.copy(xNorm = xNorm, yNorm = yNorm)))
    }

    fun onTextTimingChanged(textId: String, startMs: Long, endMs: Long) {
        val overlay = findText(textId) ?: return
        applyEdit(EditAction.UpdateText(overlay.copy(startMs = startMs, endMs = endMs)))
    }

    fun onDeleteText() {
        val textId = (_uiState.value as? EditorUiState.Ready)?.selectedTextId ?: return
        applyEdit(EditAction.DeleteText(textId))
        updateReady { it.copy(selectedTextId = null, activeTool = null) }
    }

    /** New text at the playhead, lasting [DEFAULT_TEXT_DURATION_MS], selected for editing. */
    private fun addText(ready: EditorUiState.Ready) {
        if (ready.project.clips.isEmpty()) return
        val start = previewState.value.positionMs
        val overlay = TextOverlay(id = newId(), text = "", startMs = start, endMs = start + DEFAULT_TEXT_DURATION_MS)
        applyEdit(EditAction.AddText(overlay))
        updateReady { it.copy(selectedTextId = overlay.id, selectedClipId = null, activeTool = EditorTool.TEXT) }
    }

    /** A text left empty when its panel closes is removed rather than kept invisible. */
    private fun dropBlankSelectedText() {
        val ready = _uiState.value as? EditorUiState.Ready ?: return
        val text = ready.selectedTextId?.let(::findText) ?: return
        if (text.text.isBlank()) applyEdit(EditAction.DeleteText(text.id))
    }

    private fun findText(textId: String): TextOverlay? =
        (_uiState.value as? EditorUiState.Ready)?.project?.textOverlays?.firstOrNull { it.id == textId }

    fun onMediaPicked(uris: List<String>) {
        val ready = _uiState.value as? EditorUiState.Ready ?: return
        if (uris.isEmpty() || ready.isImporting) return
        updateReady { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val result = importer.import(uris)
                if (result.imported.isEmpty()) {
                    _events.send(EditorEvent.NothingImported)
                    return@launch
                }
                if (result.failedCount > 0) _events.send(EditorEvent.SomeFilesSkipped(result.failedCount))
                applyEdit(EditAction.AddClips(result.imported.map { clipFromMedia(newId(), it.uri, it.info) }))
            } finally {
                updateReady { it.copy(isImporting = false) }
            }
        }
    }

    /** The clip a clip tool acts on: the selected one, otherwise the one under the playhead. */
    private fun targetClipId(): String? {
        val ready = _uiState.value as? EditorUiState.Ready ?: return null
        ready.selectedClipId?.let { return it }
        val position = ready.project.clipAt(previewState.value.positionMs) ?: return null
        return ready.project.clips[position.index].id
    }

    private fun applyEdit(action: EditAction) {
        val ready = _uiState.value as? EditorUiState.Ready ?: return
        val edited = EditReducer.reduce(ready.project, action)
        if (edited === ready.project) return
        val saved = edited.copy(updatedAt = clock.millis())
        _uiState.value = ready.copy(project = saved)
        previewController.pause()
        pendingPreview.value = saved
        viewModelScope.launch { repository.save(saved) }
    }

    private fun updateReady(transform: (EditorUiState.Ready) -> EditorUiState.Ready) =
        _uiState.update { if (it is EditorUiState.Ready) transform(it) else it }

    private fun newId() = UUID.randomUUID().toString()

    override fun onCleared() {
        previewController.release()
    }

    private companion object {
        const val PREVIEW_REBUILD_DEBOUNCE_MS = 150L
        const val DEFAULT_TEXT_DURATION_MS = 3_000L
        val CLIP_TOOLS = setOf(EditorTool.TRIM, EditorTool.SPEED, EditorTool.FILTER)
    }
}
