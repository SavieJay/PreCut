package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.WaveformLoader
import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.audioLanes
import com.savie.precut.model.sourceAt
import com.savie.precut.model.timelineEndMs
import com.savie.precut.model.Project
import com.savie.precut.ui.theme.ExtractedAudioBar
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.MusicBar
import com.savie.precut.ui.theme.VoiceoverBar
import kotlin.math.roundToLong

/** Callbacks from the audio rows. */
interface AudioRowActions {
    fun onSelectAudio(audioId: String)
    fun onAudioTiming(audioId: String, timelineStartMs: Long, trimStartMs: Long, trimEndMs: Long)
    fun onAddAudio()
}

/**
 * Audio clips stacked into rows (overlaps go to the next row). Only the audible part (up to the
 * video's end) is drawn, which keeps bar widths bounded by the video's length.
 */
@Composable
fun AudioRows(
    project: Project,
    selectedAudioId: String?,
    videoDurationMs: Long,
    pxPerMs: Float,
    rowHeight: Dp,
    rowGap: Dp,
    waveforms: WaveformLoader,
    snap: (ownerId: String, startMs: Long, endMs: Long, moveMs: Long) -> Long,
    actions: AudioRowActions,
) {
    val density = LocalDensity.current
    val width = with(density) { (videoDurationMs * pxPerMs).toDp() }
    val lanes = project.audioLanes()
    Column {
        if (lanes.isEmpty()) {
            Spacer(Modifier.height(rowGap))
            Box(
                Modifier
                    .height(rowHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Ink2)
                    .clickable(onClick = actions::onAddAudio)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.audio_add_audio), style = MaterialTheme.typography.labelSmall)
            }
        }
        lanes.forEach { lane ->
            Spacer(Modifier.height(rowGap))
            Box(Modifier.width(width).height(rowHeight)) {
                lane.forEach { audio ->
                    key(audio.id) {
                        AudioBar(audio, audio.id == selectedAudioId, videoDurationMs, pxPerMs, waveforms, snap, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioBar(
    audio: AudioClip,
    selected: Boolean,
    videoDurationMs: Long,
    pxPerMs: Float,
    waveforms: WaveformLoader,
    snap: (ownerId: String, startMs: Long, endMs: Long, moveMs: Long) -> Long,
    actions: AudioRowActions,
) {
    val waveform by produceState(waveforms.cached(audio.uri), audio.uri) {
        if (value == null) value = waveforms.load(audio.uri)
    }
    val full = TimelineGeometry.AudioTiming(audio.timelineStartMs, audio.trimStartMs, audio.trimEndMs)
    // The end handle trims from the audible end, so it responds even when the file runs past the video.
    val audible = full.copy(trimEndMs = minOf(audio.trimEndMs, audio.sourceAt(videoDurationMs).coerceAtLeast(audio.trimStartMs + 1)))
    fun timing(drag: BarDrag) = TimelineGeometry.dragAudio(
        if (drag.endMs != 0L) audible else full, audio.sourceDurationMs, audio.speed, drag.moveMs, drag.startMs, drag.endMs,
    )
    fun range(drag: BarDrag): Pair<Long, Long> = timing(drag).let {
        val end = it.timelineStartMs + ((it.trimEndMs - it.trimStartMs) / audio.speed).roundToLong()
        it.timelineStartMs to minOf(end, videoDurationMs).coerceAtLeast(it.timelineStartMs + 1)
    }
    val label = audio.displayName.ifBlank {
        stringResource(
            when (audio.kind) {
                AudioKind.MUSIC -> R.string.audio_music
                AudioKind.VOICEOVER -> R.string.audio_voiceover
                AudioKind.EXTRACTED -> R.string.audio_extracted
            },
        )
    }
    TimelineBar(
        id = audio.id,
        label = label,
        color = when (audio.kind) {
            AudioKind.MUSIC -> MusicBar
            AudioKind.VOICEOVER -> VoiceoverBar
            AudioKind.EXTRACTED -> ExtractedAudioBar
        },
        selected = selected,
        pxPerMs = pxPerMs,
        rangeFor = ::range,
        onSelect = { actions.onSelectAudio(audio.id) },
        onCommit = { drag -> timing(drag).let { actions.onAudioTiming(audio.id, it.timelineStartMs, it.trimStartMs, it.trimEndMs) } },
        snapMove = { move -> snap(audio.id, audio.timelineStartMs, minOf(audio.timelineEndMs(), videoDurationMs), move) },
        decoration = { _, _ -> AudioBarDecoration(audio, waveform, pxPerMs, Modifier.fillMaxHeight()) },
    )
}
