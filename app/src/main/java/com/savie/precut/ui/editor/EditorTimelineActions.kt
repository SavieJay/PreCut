package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.savie.precut.ui.editor.timeline.TimelineActions

/** Timeline callbacks routed to the ViewModel (and its audio controller). */
@Composable
fun rememberTimelineActions(viewModel: EditorViewModel): TimelineActions = remember(viewModel) {
    object : TimelineActions {
        override fun onScrubStart() = viewModel.onScrubStart()
        override fun onSeek(positionMs: Long) = viewModel.onSeek(positionMs)
        override fun onSelectClip(clipId: String?) = viewModel.onClipSelected(clipId)
        override fun onTrim(clipId: String, trimStartMs: Long, trimEndMs: Long) =
            viewModel.onTrim(clipId, trimStartMs, trimEndMs)
        override fun onMove(from: Int, to: Int) = viewModel.onMove(from, to)
        override fun onSelectText(textId: String) = viewModel.onTextSelected(textId)
        override fun onTextTiming(textId: String, startMs: Long, endMs: Long) =
            viewModel.text.onTiming(textId, startMs, endMs)
        override fun onToggleOriginalAudio() = viewModel.onToggleOriginalAudio()
        override fun onSelectAudio(audioId: String) = viewModel.onAudioSelected(audioId)
        override fun onAudioTiming(audioId: String, timelineStartMs: Long, trimStartMs: Long, trimEndMs: Long) =
            viewModel.audio.onTiming(audioId, timelineStartMs, trimStartMs, trimEndMs)
        override fun onAddAudio() = viewModel.onAudioMenu(true)
    }
}
