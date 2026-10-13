package com.savie.precut.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.savie.precut.data.ProjectRepository
import com.savie.precut.di.ApplicationScope
import com.savie.precut.media.MediaImporter
import com.savie.precut.media.MediaProbe
import com.savie.precut.media.PreviewController
import com.savie.precut.media.PreviewState
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.media.VoiceRecorder
import com.savie.precut.media.WaveformLoader
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
import kotlinx.coroutines.CoroutineScope
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

/**
 * Panels that replace the toolbar while open. VOLUME, FADE and SPEED act on the selected audio
 * clip when there is one, otherwise on a video clip.
 */
enum class EditorTool { TRIM, SPEED, FILTER, VOLUME, FADE, TEXT, RATIO, VOICE, BEATS, VOICEOVER }

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object NotFound : EditorUiState
    data class Ready(
        val project: Project,
        val selectedClipId: String? = null,
        val selectedTextId: String? = null,
        val selectedAudioId: String? = null,
        val activeTool: EditorTool? = null,
        /** The Audio sub-toolbar (Music, Extract, Record) is showing. */
        val audioMenuOpen: Boolean = false,
        val isImporting: Boolean = false,
        /** Clips and audio clips whose media can't be opened any more. */
        val missingIds: Set<String> = emptySet(),
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
    ) : EditorUiState
}

sealed interface EditorEvent {
    data class SomeFilesSkipped(val count: Int) : EditorEvent
    data object NothingImported : EditorEvent
    data object SpeedNotForPhotos : EditorEvent
    /** The screen should open the audio file picker. */
    data object PickMusic : EditorEvent
    data object MusicImportFailed : EditorEvent
    /** The screen should ask for RECORD_AUDIO, then call back into the audio controller. */
    data object RequestMicPermission : EditorEvent
    data object RecordingFailed : EditorEvent
    data object NothingToExtract : EditorEvent
    /** The screen should open the single-item media picker to replace the selected clip. */
    data object PickReplacement : EditorEvent
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
    /** For audio clips' waveforms. */
    val waveforms: WaveformLoader,
    private val clock: Clock,
    voiceRecorder: VoiceRecorder,
    private val probe: MediaProbe,
    @ApplicationScope private val appScope: CoroutineScope,
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

    private val host = object : EditorHost {
        override val ready: EditorUiState.Ready? get() = _uiState.value as? EditorUiState.Ready
        override val positionMs: Long get() = previewState.value.positionMs
        override val scope get() = viewModelScope
        override fun applyEdit(action: EditAction) = this@EditorViewModel.applyEdit(action)
        override fun update(transform: (EditorUiState.Ready) -> EditorUiState.Ready) = updateReady(transform)
        override fun send(event: EditorEvent) {
            viewModelScope.launch { _events.send(event) }
        }
        override fun newId(): String = this@EditorViewModel.newId()
        override fun targetClipId(): String? = this@EditorViewModel.targetClipId()
    }

    /** Audio lanes, extract, voiceover and audio-clip tools. */
    val audio = EditorAudio(host, importer, previewController, voiceRecorder)

    /** Edits, undo history and auto-save; set once the project has loaded. */
    private var session: ProjectSession? = null

    /** Latest edited project; the preview rebuilds once edits pause, so rapid edits rebuild once. */
    private val pendingPreview = MutableStateFlow<Project?>(null)

    init {
        viewModelScope.launch {
            val project = repository.load(projectId)
            if (project == null) {
                _uiState.value = EditorUiState.NotFound
                return@launch
            }
            session = ProjectSession(project, repository, clock, appScope, viewModelScope)
            val missing = findMissing(project)
            previewController.setProject(playable(project, missing))
            _uiState.value = EditorUiState.Ready(project, missingIds = missing)
        }
        viewModelScope.launch {
            pendingPreview.filterNotNull().debounce(PREVIEW_REBUILD_DEBOUNCE_MS).collect { project ->
                val missing = (_uiState.value as? EditorUiState.Ready)?.missingIds.orEmpty()
                val playable = playable(project, missing)
                val resumeAt = previewState.value.positionMs.coerceAtMost(playable.totalDurationMs())
                previewController.setProject(playable, resumeAt)
            }
        }
    }

    // Playback

    fun onPlayPause() = previewController.togglePlayPause()

    fun onScrubStart() = previewController.pause()

    fun onSeek(positionMs: Long) = previewController.seekTo(positionMs)

    /** Leaving the screen pauses playback and finishes (keeps) any voiceover in progress. */
    fun onScreenStopped() {
        audio.onRecordStop()
        previewController.pause()
        session?.flush()
    }

    // Undo / redo

    fun onUndo() = session?.undo()?.let(::showRestored)

    fun onRedo() = session?.redo()?.let(::showRestored)

    /** Shows an undone/redone project, dropping selections that no longer exist. */
    private fun showRestored(project: Project) {
        val history = session?.history ?: return
        updateReady { ready ->
            ready.copy(
                project = project,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                selectedClipId = ready.selectedClipId?.takeIf { id -> project.clips.any { it.id == id } },
                selectedTextId = ready.selectedTextId?.takeIf { id -> project.textOverlays.any { it.id == id } },
                selectedAudioId = ready.selectedAudioId?.takeIf { id -> project.audioClips.any { it.id == id } },
                activeTool = null,
            )
        }
        previewController.pause()
        pendingPreview.value = project
    }

    // Missing media

    fun onRemoveMissing() {
        val ready = _uiState.value as? EditorUiState.Ready ?: return
        val clipIds = ready.project.clips.map { it.id }.filter { it in ready.missingIds }.toSet()
        val audioIds = ready.project.audioClips.map { it.id }.filter { it in ready.missingIds }.toSet()
        applyEdit(EditAction.RemoveItems(clipIds, audioIds))
        updateReady { it.copy(missingIds = emptySet(), selectedClipId = null, selectedAudioId = null, activeTool = null) }
    }

    /** Replace the selected (or playhead) clip's media, e.g. a missing one. */
    fun onReplaceClip() {
        val clipId = targetClipId() ?: return
        updateReady { it.copy(selectedClipId = clipId) }
        viewModelScope.launch { _events.send(EditorEvent.PickReplacement) }
    }

    fun onReplacementPicked(uri: String?) {
        val clipId = (_uiState.value as? EditorUiState.Ready)?.selectedClipId ?: return
        if (uri == null) return
        viewModelScope.launch {
            val imported = importer.import(listOf(uri)).imported.firstOrNull()
            if (imported == null) {
                _events.send(EditorEvent.NothingImported)
                return@launch
            }
            updateReady { it.copy(missingIds = it.missingIds - clipId) }
            applyEdit(EditAction.ReplaceClip(clipId, imported.uri, imported.info))
        }
    }

    private suspend fun findMissing(project: Project): Set<String> =
        (project.clips.map { it.id to it.uri } + project.audioClips.map { it.id to it.uri })
            .filterNot { (_, uri) -> probe.isReadable(uri) }
            .map { it.first }
            .toSet()

    /** The project without media that can't be opened, so the rest still previews. */
    private fun playable(project: Project, missing: Set<String>): Project =
        if (missing.isEmpty()) {
            project
        } else {
            project.copy(clips = project.clips.filterNot { it.id in missing }, audioClips = project.audioClips.filterNot { it.id in missing })
        }

    // Selection and tools

    fun onClipSelected(clipId: String?) {
        dropBlankSelectedText()
        updateReady {
            val clip = it.project.clips.firstOrNull { c -> c.id == clipId }
            // Keep a clip tool open when switching clips, unless it doesn't apply (speed on a photo).
            val keepTool = clip != null && it.activeTool in CLIP_TOOLS &&
                !(it.activeTool == EditorTool.SPEED && clip.type == ClipType.IMAGE)
            it.copy(
                selectedClipId = clipId,
                selectedTextId = null,
                selectedAudioId = null,
                audioMenuOpen = it.audioMenuOpen && clipId == null,
                activeTool = if (keepTool) it.activeTool else null,
            )
        }
    }

    fun onTextSelected(textId: String?) {
        dropBlankSelectedText()
        updateReady {
            it.copy(
                selectedTextId = textId,
                selectedClipId = null,
                selectedAudioId = null,
                audioMenuOpen = false,
                activeTool = if (textId != null) EditorTool.TEXT else null,
            )
        }
    }

    fun onToolSelected(tool: EditorTool) {
        val ready = _uiState.value as? EditorUiState.Ready ?: return
        if (ready.activeTool == tool) return onToolClosed()
        if (ready.selectedAudioId != null && tool in AUDIO_TOOLS) {
            updateReady { it.copy(activeTool = tool) }
            return
        }
        when (tool) {
            EditorTool.RATIO, EditorTool.VOICEOVER -> updateReady { it.copy(activeTool = tool) }
            EditorTool.TEXT -> addText(ready)
            EditorTool.VOICE, EditorTool.BEATS -> Unit // audio-only tools
            EditorTool.TRIM, EditorTool.SPEED, EditorTool.FILTER, EditorTool.VOLUME, EditorTool.FADE -> {
                val clipId = targetClipId() ?: return
                val clip = ready.project.clips.first { it.id == clipId }
                if (tool == EditorTool.SPEED && clip.type == ClipType.IMAGE) {
                    viewModelScope.launch { _events.send(EditorEvent.SpeedNotForPhotos) }
                    return
                }
                updateReady { it.copy(selectedClipId = clipId, selectedTextId = null, selectedAudioId = null, activeTool = tool) }
            }
        }
    }

    fun onToolClosed() {
        dropBlankSelectedText()
        // Audio selection stays so its toolbar comes back, like CapCut.
        updateReady { it.copy(activeTool = null, selectedTextId = null) }
    }

    fun onAudioMenu(open: Boolean) = updateReady {
        it.copy(audioMenuOpen = open, selectedClipId = null, selectedTextId = null, selectedAudioId = null, activeTool = null)
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

    fun onClipVolumeChanged(volume: Float) {
        val clipId = (_uiState.value as? EditorUiState.Ready)?.selectedClipId ?: return
        applyEdit(EditAction.SetClipVolume(clipId, volume))
    }

    fun onClipMutedChanged(muted: Boolean) {
        val clipId = (_uiState.value as? EditorUiState.Ready)?.selectedClipId ?: return
        applyEdit(EditAction.SetClipMuted(clipId, muted))
    }

    fun onClipFadesChanged(fadeInMs: Long, fadeOutMs: Long) {
        val clipId = (_uiState.value as? EditorUiState.Ready)?.selectedClipId ?: return
        applyEdit(EditAction.SetClipFades(clipId, fadeInMs, fadeOutMs))
    }

    /** CapCut's "Mute clip audio" switch beside the timeline. */
    fun onToggleOriginalAudio() {
        val ready = _uiState.value as? EditorUiState.Ready ?: return
        applyEdit(EditAction.SetOriginalAudioMuted(!ready.project.originalAudioMuted))
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
        val currentSession = session ?: return
        val edited = currentSession.apply(action) ?: return
        _uiState.value = ready.copy(project = edited, canUndo = currentSession.history.canUndo, canRedo = false)
        previewController.pause()
        pendingPreview.value = edited
    }

    private fun updateReady(transform: (EditorUiState.Ready) -> EditorUiState.Ready) =
        _uiState.update { if (it is EditorUiState.Ready) transform(it) else it }

    private fun newId() = UUID.randomUUID().toString()

    override fun onCleared() {
        session?.flush()
        audio.release()
        previewController.release()
    }

    private companion object {
        const val PREVIEW_REBUILD_DEBOUNCE_MS = 150L
        const val DEFAULT_TEXT_DURATION_MS = 3_000L
        val CLIP_TOOLS = setOf(EditorTool.TRIM, EditorTool.SPEED, EditorTool.FILTER, EditorTool.VOLUME, EditorTool.FADE)
        val AUDIO_TOOLS = setOf(EditorTool.VOLUME, EditorTool.FADE, EditorTool.SPEED, EditorTool.VOICE, EditorTool.BEATS)
    }
}
