package com.savie.precut.ui.editor

import com.savie.precut.model.Project
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.SpokenWord
import com.savie.precut.model.edit.TimeRange
import com.savie.precut.model.edit.fillerIndices
import com.savie.precut.model.edit.silenceRanges
import com.savie.precut.model.edit.spokenWords
import com.savie.precut.model.edit.transcriptCuts
import com.savie.precut.model.edit.withoutTimeRanges
import com.savie.precut.model.pro.ProFeature
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The transcript panel: the captions' words, the ones marked to cut, and the silences found. */
data class TranscriptUi(
    val words: List<SpokenWord>,
    val fillers: Set<Int>,
    val silences: List<TimeRange>,
    val marked: Set<Int> = emptySet(),
    val silencesMarked: Boolean = false,
    val previewing: Boolean = false,
) {
    val cutCount: Int get() = marked.size + if (silencesMarked) silences.size else 0
}

/**
 * Edit by transcript (Phase 26B, Pro): cut the video by striking words in its captions, with
 * one-tap Remove filler words and Remove silences; Preview plays the cut version; Apply cuts is one
 * undo step that closes the gaps across the whole project ([EditAction.RemoveTimeRanges]).
 */
class EditorTranscript(private val host: EditorHost, private val pro: EditorPro) {
    private val _state = MutableStateFlow<TranscriptUi?>(null)
    val state: StateFlow<TranscriptUi?> = _state.asStateFlow()

    fun open() = pro.gate(ProFeature.TRANSCRIPT_EDIT) {
        val project = host.ready?.project ?: return@gate
        val words = project.spokenWords()
        _state.value = TranscriptUi(words, fillerIndices(words), silenceRanges(words, project.totalDurationMs()))
        host.update { it.copy(activeTool = EditorTool.TRANSCRIPT, selectedClipId = null, selectedTextId = null, selectedAudioId = null, selectedLayerId = null) }
    }

    fun onWord(index: Int) {
        _state.update { ui -> ui?.copy(marked = if (index in ui.marked) ui.marked - index else ui.marked + index) }
        refreshPreview()
    }

    fun onFillers() {
        _state.update { ui -> ui?.copy(marked = if (ui.fillers.all { it in ui.marked }) ui.marked - ui.fillers else ui.marked + ui.fillers) }
        refreshPreview()
    }

    fun onSilences() {
        _state.update { ui -> ui?.copy(silencesMarked = !ui.silencesMarked) }
        refreshPreview()
    }

    /** Before/after: the preview plays the project with the cuts, without applying them. */
    fun onPreview() {
        _state.update { ui -> ui?.copy(previewing = !ui.previewing) }
        refreshPreview()
    }

    fun onApply() {
        val ui = _state.value ?: return
        val project = host.ready?.project ?: return
        val cuts = cutsFor(project, ui)
        if (cuts.isEmpty()) return
        close()
        pro.spend(ProFeature.TRANSCRIPT_EDIT)
        host.applyEdit(EditAction.RemoveTimeRanges(cuts))
        host.send(EditorEvent.TranscriptCut(cuts.sumOf { it.lengthMs }))
    }

    fun close() {
        if (_state.value?.previewing == true) host.showInstead(null)
        _state.value = null
        host.update { if (it.activeTool == EditorTool.TRANSCRIPT) it.copy(activeTool = null) else it }
    }

    private fun refreshPreview() {
        val ui = _state.value ?: return
        val project = host.ready?.project ?: return
        host.showInstead(if (ui.previewing) project.withoutTimeRanges(cutsFor(project, ui)) else null)
    }

    private fun cutsFor(project: Project, ui: TranscriptUi): List<TimeRange> =
        project.transcriptCuts(ui.words, ui.marked, if (ui.silencesMarked) ui.silences else emptyList())
}
