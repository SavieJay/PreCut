package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.media.WaveformLoader
import com.savie.precut.model.Project
import com.savie.precut.model.audioLanes
import com.savie.precut.model.textLanes
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.timelineAt
import com.savie.precut.model.timelineEndMs
import com.savie.precut.model.totalDurationMs

internal val VerticalPadding = 8.dp
internal val RulerHeight = 18.dp
internal val LaneGap = 6.dp
internal val ClipHeight = 56.dp
private val TextLaneHeight = 22.dp
private val AudioRowHeight = 24.dp

/** Height the timeline needs for its text and audio rows (at least one of each). */
fun timelineHeight(project: Project): Dp {
    val textRows = project.textLanes().size.coerceAtLeast(1)
    val audioRows = project.audioLanes().size.coerceAtLeast(1)
    return VerticalPadding * 2 + RulerHeight + LaneGap + ClipHeight + LaneGap +
        TextLaneHeight * textRows + LaneGap * (textRows - 1) + (LaneGap + AudioRowHeight) * audioRows
}

/** What's selected on the timeline; at most one of the single selections is set. */
data class TimelineSelection(
    val clipId: String? = null,
    val textId: String? = null,
    val audioId: String? = null,
    /** Items whose media is missing (drawn in red). */
    val missingIds: Set<String> = emptySet(),
    /** Items picked in multi-select mode (highlighted, no handles). */
    val multiIds: Set<String> = emptySet(),
)

/** Ruler, clip strip, text rows and audio rows, stacked; laid out in timeline px. */
@Composable
fun TimelineLanes(
    project: Project,
    selection: TimelineSelection,
    pxPerMs: Float,
    visiblePx: ClosedFloatingPointRange<Float>,
    loader: ThumbnailLoader,
    waveforms: WaveformLoader,
    playheadMs: () -> Long,
    actions: TimelineActions,
) {
    val density = LocalDensity.current
    val totalMs = project.totalDurationMs()
    val contentWidthPx = TimelineGeometry.msToPx(totalMs, pxPerMs)
    val snapThresholdMs = (with(density) { 10.dp.toPx() } / pxPerMs).toLong()
    val snapTargets = snapTargetsOf(project)
    val snap = { ownerId: String, start: Long, end: Long, move: Long ->
        val targets = snapTargets.filter { it.first != ownerId }.map { it.second } + playheadMs()
        TimelineGeometry.snapMove(start, end, move, targets, snapThresholdMs)
    }
    val trimTargets = { snapTargets.map { it.second } + playheadMs() }

    TimelineRuler(
        totalMs = totalMs,
        pxPerMs = pxPerMs,
        modifier = Modifier
            .width(with(density) { contentWidthPx.toDp() })
            .height(RulerHeight),
    )
    Spacer(Modifier.height(LaneGap))
    ClipStrip(
        project = project,
        selectedClipId = selection.clipId,
        multiIds = selection.multiIds,
        missingIds = selection.missingIds,
        pxPerMs = pxPerMs,
        clipHeight = ClipHeight,
        visiblePx = visiblePx,
        loader = loader,
        snapTargets = trimTargets,
        snapThresholdMs = snapThresholdMs,
        onTrim = actions::onTrim,
    )
    Spacer(Modifier.height(LaneGap))
    TextRows(
        lanes = project.textLanes(),
        selectedTextId = selection.textId,
        multiIds = selection.multiIds,
        pxPerMs = pxPerMs,
        widthPx = contentWidthPx,
        rowHeight = TextLaneHeight,
        rowGap = LaneGap,
        snap = snap,
        onSelect = actions::onSelectText,
        onTiming = actions::onTextTiming,
    )
    AudioRows(
        project = project,
        selectedAudioId = selection.audioId,
        multiIds = selection.multiIds,
        videoDurationMs = totalMs,
        pxPerMs = pxPerMs,
        rowHeight = AudioRowHeight,
        rowGap = LaneGap,
        waveforms = waveforms,
        snap = snap,
        actions = actions,
    )
}

/** Edges and beats bars can snap to, tagged with the id of the item they belong to. */
private fun snapTargetsOf(project: Project): List<Pair<String, Long>> = buildList {
    project.clipStartTimesMs().forEach { add("" to it) }
    add("" to project.totalDurationMs())
    project.textOverlays.forEach { add(it.id to it.startMs); add(it.id to it.endMs) }
    project.audioClips.forEach { audio ->
        add(audio.id to audio.timelineStartMs)
        add(audio.id to audio.timelineEndMs())
        audio.beatsMs.forEach { add(audio.id to audio.timelineAt(it)) }
    }
}
