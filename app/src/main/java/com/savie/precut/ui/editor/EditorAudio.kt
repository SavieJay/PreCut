package com.savie.precut.ui.editor

import com.savie.precut.media.MediaImporter
import com.savie.precut.media.PreviewController
import com.savie.precut.media.VoiceRecorder
import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.ClipType
import com.savie.precut.model.VoiceEffect
import com.savie.precut.model.edit.EditAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.os.SystemClock

/** An in-progress voiceover: where it will be placed and how long it has run. */
data class RecordingState(val startMs: Long, val elapsedMs: Long)

/**
 * Audio editing for the editor: music import/replace, extract, voiceover recording and the
 * selected audio clip's tools. All edits go through [EditorHost.applyEdit] (the reducer).
 */
class EditorAudio(
    private val host: EditorHost,
    private val importer: MediaImporter,
    private val preview: PreviewController,
    private val recorder: VoiceRecorder,
) {
    private val _recording = MutableStateFlow<RecordingState?>(null)
    val recording: StateFlow<RecordingState?> = _recording.asStateFlow()

    private var replacingAudioId: String? = null
    private var recordingTicker: Job? = null

    // Selection

    fun onSelected(audioId: String) = host.update {
        it.copy(selectedAudioId = audioId, selectedClipId = null, selectedTextId = null, audioMenuOpen = false, activeTool = null)
    }

    // Adding audio

    fun onAddMusic() {
        replacingAudioId = null
        host.send(EditorEvent.PickMusic)
    }

    fun onReplace() {
        replacingAudioId = host.ready?.selectedAudioId ?: return
        host.send(EditorEvent.PickMusic)
    }

    /** New music goes in at the playhead (CapCut); a replacement keeps its slot. */
    fun onMusicPicked(uri: String?) {
        if (uri == null) return
        val replacing = replacingAudioId
        replacingAudioId = null
        if (host.ready?.isImporting != false) return
        host.update { it.copy(isImporting = true) }
        host.scope.launch {
            try {
                val imported = importer.importAudio(uri)
                if (imported == null) {
                    host.send(EditorEvent.MusicImportFailed)
                    return@launch
                }
                if (replacing != null) {
                    host.applyEdit(EditAction.ReplaceAudio(replacing, imported.uri, imported.durationMs, imported.displayName))
                } else {
                    val id = host.newId()
                    host.applyEdit(
                        EditAction.AddAudio(
                            AudioClip(
                                id = id,
                                uri = imported.uri,
                                sourceDurationMs = imported.durationMs,
                                timelineStartMs = host.positionMs,
                                trimEndMs = imported.durationMs,
                                displayName = imported.displayName,
                            ),
                        ),
                    )
                    onSelected(id)
                }
            } finally {
                host.update { it.copy(isImporting = false) }
            }
        }
    }

    /** Pulls the target video clip's sound onto an audio lane and mutes the clip. */
    fun onExtract() {
        val ready = host.ready ?: return
        val clip = host.targetClipId()?.let { id -> ready.project.clips.first { it.id == id } }
        if (clip == null || clip.type != ClipType.VIDEO) {
            host.send(EditorEvent.NothingToExtract)
            return
        }
        val id = host.newId()
        host.applyEdit(EditAction.ExtractAudio(clip.id, id))
        onSelected(id)
    }

    // Voiceover

    /** Called after RECORD_AUDIO is granted (the screen requests it on [EditorEvent.RequestMicPermission]). */
    fun onRecordStart(hasPermission: Boolean) {
        if (!hasPermission) {
            host.send(EditorEvent.RequestMicPermission)
            return
        }
        if (_recording.value != null) return
        if (!recorder.start()) {
            host.send(EditorEvent.RecordingFailed)
            return
        }
        val startMs = host.positionMs
        val startedAt = SystemClock.elapsedRealtime()
        _recording.value = RecordingState(startMs, 0L)
        preview.setMuted(true)
        preview.play()
        recordingTicker = host.scope.launch {
            while (true) {
                _recording.value = RecordingState(startMs, SystemClock.elapsedRealtime() - startedAt)
                delay(100)
            }
        }
    }

    fun onRecordStop() {
        val state = _recording.value ?: return
        recordingTicker?.cancel()
        _recording.value = null
        preview.pause()
        preview.setMuted(false)
        val uri = recorder.stop()
        if (uri == null) {
            host.send(EditorEvent.RecordingFailed)
            return
        }
        host.scope.launch {
            val duration = importer.importAudio(uri)?.durationMs
            if (duration == null) {
                host.send(EditorEvent.RecordingFailed)
                return@launch
            }
            val id = host.newId()
            host.applyEdit(
                EditAction.AddAudio(
                    AudioClip(
                        id = id,
                        uri = uri,
                        sourceDurationMs = duration,
                        timelineStartMs = state.startMs,
                        trimEndMs = duration,
                        kind = AudioKind.VOICEOVER,
                    ),
                ),
            )
            onSelected(id)
        }
    }

    // Selected audio clip

    private fun selected(): AudioClip? {
        val ready = host.ready ?: return null
        return ready.project.audioClips.firstOrNull { it.id == ready.selectedAudioId }
    }

    private fun edit(action: (AudioClip) -> EditAction) {
        selected()?.let { host.applyEdit(action(it)) }
    }

    fun onTiming(audioId: String, timelineStartMs: Long, trimStartMs: Long, trimEndMs: Long) =
        host.applyEdit(EditAction.SetAudioTiming(audioId, timelineStartMs, trimStartMs, trimEndMs))

    fun onVolume(volume: Float) = edit { EditAction.SetAudioVolume(it.id, volume) }

    fun onFades(fadeInMs: Long, fadeOutMs: Long) = edit { EditAction.SetAudioFades(it.id, fadeInMs, fadeOutMs) }

    fun onSpeed(speed: Float) = edit { EditAction.SetAudioSpeed(it.id, speed) }

    fun onVoiceEffect(effect: VoiceEffect) = edit { EditAction.SetVoiceEffect(it.id, effect) }

    fun onToggleBeat() = edit { EditAction.ToggleBeat(it.id, host.positionMs) }

    fun onClearBeats() = edit { EditAction.ClearBeats(it.id) }

    fun onSplit() = edit { EditAction.SplitAudio(it.id, host.positionMs, host.newId()) }

    fun onDuplicate() {
        val audio = selected() ?: return
        val id = host.newId()
        host.applyEdit(EditAction.DuplicateAudio(audio.id, id))
        onSelected(id)
    }

    fun onDelete() {
        val audio = selected() ?: return
        host.applyEdit(EditAction.DeleteAudio(audio.id))
        host.update { it.copy(selectedAudioId = null, activeTool = null) }
    }

    /** Stops any recording without keeping it; call from ViewModel.onCleared. */
    fun release() {
        recordingTicker?.cancel()
        recorder.release()
    }
}
