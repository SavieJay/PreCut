package com.savie.precut.ui.editor

import com.savie.precut.data.AppSettings
import com.savie.precut.data.SubtitleFiles
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.text.CaptionStyle
import com.savie.precut.model.text.Srt
import com.savie.precut.model.text.captionCount
import com.savie.precut.model.text.captionSplitIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Captions panel (Phase 21B): every line with its time, one opened at a time to edit its
 * words, nudge its times, split, merge or delete it; a style for all; the SRT file.
 */
class EditorCaptionEdits(
    private val host: EditorHost,
    private val files: SubtitleFiles,
    private val settings: AppSettings,
) {
    private val _editing = MutableStateFlow<String?>(null)

    /** The caption opened in the panel, if any. */
    val editing: StateFlow<String?> = _editing.asStateFlow()

    /** Opens the panel; nothing is selected, so it isn't hidden behind a selection's panel. */
    fun open() {
        if ((host.ready?.project?.captionCount() ?: 0) == 0) return
        _editing.value = null
        host.update {
            it.copy(activeTool = EditorTool.CAPTIONS, selectedClipId = null, selectedTextId = null, selectedAudioId = null, selectedLayerId = null)
        }
    }

    /** Opens [id] for editing (or closes it again) and moves the playhead to it. */
    fun onRow(id: String) {
        val caption = caption(id) ?: return
        _editing.value = if (_editing.value == id) null else id
        host.seekTo(caption.startMs)
    }

    fun onText(id: String, text: String) = host.applyEdit(EditAction.SetCaptionText(id, text))

    /** Moves [id]'s start and end by these amounts (kept between its neighbours). */
    fun onNudge(id: String, startDeltaMs: Long, endDeltaMs: Long) {
        val caption = caption(id) ?: return
        host.applyEdit(EditAction.SetCaptionTimes(id, caption.startMs + startDeltaMs, caption.endMs + endDeltaMs))
    }

    /** Cuts [id] at the playhead when it's inside the caption, otherwise between its middle words. */
    fun onSplit(id: String) {
        val caption = caption(id) ?: return
        val words = caption.caption?.words.orEmpty()
        val playhead = host.positionMs
        val index = if (playhead > caption.startMs && playhead < caption.endMs) caption.captionSplitIndex(playhead) else words.size / 2
        index?.let { host.applyEdit(EditAction.SplitCaption(id, it, host.newId())) }
    }

    fun onMerge(id: String) = host.applyEdit(EditAction.MergeCaptionWithNext(id))

    fun onDelete(id: String) {
        host.applyEdit(EditAction.DeleteText(id))
        _editing.value = null
        if ((host.ready?.project?.captionCount() ?: 0) == 0) host.update { it.copy(activeTool = null) }
    }

    fun onRestyle(style: CaptionStyle) {
        host.applyEdit(EditAction.RestyleCaptions(style))
        settings.update { it.copy(captionStyleId = style.id) }
    }

    /** Asks the screen for a place to save the SRT file. */
    fun onSaveSrt() {
        val project = host.ready?.project ?: return
        if (project.captionCount() == 0) return
        host.send(EditorEvent.PickSubtitlesTarget(Srt.fileName(project.name)))
    }

    /** The place picked for the SRT file; null when the picker was cancelled. */
    fun onSrtTarget(uri: String?) {
        if (uri == null) return
        val srt = host.ready?.project?.let { Srt.of(it.textOverlays) } ?: return
        host.scope.launch { host.send(if (files.save(uri, srt)) EditorEvent.SubtitlesSaved else EditorEvent.SubtitlesNotSaved) }
    }

    /** Writes the SRT file into app storage and asks the screen to share it. */
    fun onShareSrt() {
        val project = host.ready?.project ?: return
        val srt = Srt.of(project.textOverlays) ?: return
        host.scope.launch {
            val uri = files.forSharing(Srt.fileName(project.name), srt)
            host.send(if (uri != null) EditorEvent.ShareSubtitles(uri) else EditorEvent.SubtitlesNotSaved)
        }
    }

    private fun caption(id: String): TextOverlay? = host.ready?.project?.textOverlays?.firstOrNull { it.id == id && it.caption != null }
}
