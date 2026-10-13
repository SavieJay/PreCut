package com.savie.precut.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.model.edit.MIN_CLIP_DURATION_MS
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.ui.editor.tools.AudioMenuBar
import com.savie.precut.ui.editor.tools.ClipVolumeTool
import com.savie.precut.ui.editor.tools.FadeTool
import com.savie.precut.ui.editor.tools.FilterTool
import com.savie.precut.ui.editor.tools.RatioTool
import com.savie.precut.ui.editor.tools.SpeedTool
import com.savie.precut.ui.editor.tools.TextTool
import com.savie.precut.ui.editor.tools.ToolPanel
import com.savie.precut.ui.editor.tools.TrimTool
import com.savie.precut.ui.editor.tools.VoiceoverTool
import com.savie.precut.ui.editor.tools.VolumeSlider

/**
 * The open tool's panel, or the toolbar for what's selected: an audio clip, the Audio menu, or the
 * main toolbar. Audio-clip panels live in [AudioPanels].
 */
@Composable
fun EditorBottomPanel(state: EditorUiState.Ready, canSplit: Boolean, positionMs: Long, viewModel: EditorViewModel) {
    val context = LocalContext.current
    val recording by viewModel.audio.recording.collectAsStateWithLifecycle()
    val clip = state.project.clips.firstOrNull { it.id == state.selectedClipId }
    val text = state.project.textOverlays.firstOrNull { it.id == state.selectedTextId }
    val audio = state.project.audioClips.firstOrNull { it.id == state.selectedAudioId }
    val close = viewModel::onToolClosed

    if (audio != null) {
        AudioPanels(audio, state.activeTool, positionMs, viewModel)
        return
    }
    when {
        state.activeTool == EditorTool.TRIM && clip != null ->
            TrimTool(clip, onTrim = { start, end -> viewModel.onTrim(clip.id, start, end) }, onDone = close)
        state.activeTool == EditorTool.SPEED && clip != null ->
            SpeedTool(clip.speed, onSpeedChanged = viewModel::onSpeedChanged, onDone = close)
        state.activeTool == EditorTool.FILTER && clip != null ->
            FilterTool(clip, viewModel.thumbnails, onFilterSelected = viewModel::onFilterSelected, onDone = close)
        state.activeTool == EditorTool.VOLUME && clip != null ->
            ClipVolumeTool(clip, viewModel::onClipVolumeChanged, viewModel::onClipMutedChanged, onDone = close)
        state.activeTool == EditorTool.FADE && clip != null ->
            FadeTool(clip.fadeInMs, clip.fadeOutMs, clip.timelineDurationMs(), viewModel::onClipFadesChanged, onDone = close)
        state.activeTool == EditorTool.TEXT && text != null ->
            TextTool(text, onChange = viewModel::onTextChanged, onDelete = viewModel::onDeleteText, onDone = close)
        state.activeTool == EditorTool.RATIO ->
            RatioTool(state.project.aspectRatio, onSelected = viewModel::onAspectRatioSelected, onDone = close)
        state.activeTool == EditorTool.VOICEOVER -> VoiceoverTool(
            recording = recording,
            playheadMs = positionMs,
            onRecord = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                viewModel.audio.onRecordStart(granted)
            },
            onStop = viewModel.audio::onRecordStop,
            onDone = close,
        )
        state.audioMenuOpen -> AudioMenuBar(
            onBack = { viewModel.onAudioMenu(false) },
            onMusic = viewModel.audio::onAddMusic,
            onExtract = viewModel.audio::onExtract,
            onRecord = { viewModel.onToolSelected(EditorTool.VOICEOVER) },
        )
        else -> MainToolbar(state, canSplit, viewModel)
    }
}

/** Volume panel for an audio clip. */
@Composable
fun AudioVolumeTool(volume: Float, onVolumeChanged: (Float) -> Unit, onDone: () -> Unit) {
    ToolPanel(stringResource(R.string.tool_volume), onDone) {
        VolumeSlider(volume, enabled = true, onVolumeChanged = onVolumeChanged)
    }
}

/** Whether the playhead is far enough inside [startMs, endMs) for a split to leave two usable halves. */
fun canSplitAt(positionMs: Long, startMs: Long, endMs: Long): Boolean =
    positionMs > startMs + MIN_CLIP_DURATION_MS && positionMs < endMs - MIN_CLIP_DURATION_MS
