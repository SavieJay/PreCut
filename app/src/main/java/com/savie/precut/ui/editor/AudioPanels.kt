package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.savie.precut.model.AudioClip
import com.savie.precut.model.SpeedScale
import com.savie.precut.model.edit.BEAT_TOLERANCE_MS
import com.savie.precut.model.sourceAt
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineEndMs
import com.savie.precut.ui.editor.tools.AudioToolbar
import com.savie.precut.ui.editor.tools.AudioToolbarActions
import com.savie.precut.ui.editor.tools.BeatsTool
import com.savie.precut.ui.editor.tools.FadeTool
import com.savie.precut.ui.editor.tools.AUDIO_SPEED_PRESETS
import com.savie.precut.ui.editor.tools.SpeedTool
import com.savie.precut.ui.editor.tools.VoiceEffectTool
import kotlin.math.abs

/** Panels and toolbar for the selected audio clip. */
@Composable
fun AudioPanels(audio: AudioClip, activeTool: EditorTool?, positionMs: () -> Long, viewModel: EditorViewModel) {
    val controller = viewModel.audio
    val close = viewModel::onToolClosed
    when (activeTool) {
        EditorTool.VOLUME -> AudioVolumeTool(audio.volume, controller::onVolume, onDone = close)
        EditorTool.FADE -> FadeTool(audio.fadeInMs, audio.fadeOutMs, audio.timelineDurationMs(), controller::onFades, onDone = close)
        EditorTool.SPEED -> SpeedTool(
            audio.speed, onSpeedChanged = controller::onSpeed, onDone = close,
            scale = SpeedScale.AUDIO, presets = AUDIO_SPEED_PRESETS, sourceLengthMs = audio.trimEndMs - audio.trimStartMs,
        )
        EditorTool.VOICE -> VoiceEffectTool(audio.voiceEffect, controller::onVoiceEffect, onDone = close)
        EditorTool.BEATS -> BeatsTool(
            beatCount = audio.beatsMs.size,
            beatAtPlayhead = rememberBeatAtPlayhead(audio, positionMs),
            playheadMs = positionMs(),
            onToggleBeat = controller::onToggleBeat,
            onClear = controller::onClearBeats,
            onDone = close,
        )
        else -> AudioToolbar(
            canSplit = rememberCanSplit(positionMs, audio.timelineStartMs, audio.timelineEndMs()),
            actions = object : AudioToolbarActions {
                override fun onVolume() = viewModel.onToolSelected(EditorTool.VOLUME)
                override fun onFade() = viewModel.onToolSelected(EditorTool.FADE)
                override fun onSplit() = controller.onSplit()
                override fun onSpeed() = viewModel.onToolSelected(EditorTool.SPEED)
                override fun onVoice() = viewModel.onToolSelected(EditorTool.VOICE)
                override fun onBeats() = viewModel.onToolSelected(EditorTool.BEATS)
                override fun onReplace() = controller.onReplace()
                override fun onDuplicate() = controller.onDuplicate()
                override fun onDelete() = controller.onDelete()
            },
        )
    }
}

/** Whether a beat sits under the playhead; recomposes only when that changes. */
@Composable
private fun rememberBeatAtPlayhead(audio: AudioClip, positionMs: () -> Long): Boolean {
    val atBeat by remember(audio) {
        derivedStateOf { audio.beatsMs.any { abs(it - audio.sourceAt(positionMs())) <= BEAT_TOLERANCE_MS } }
    }
    return atBeat
}
