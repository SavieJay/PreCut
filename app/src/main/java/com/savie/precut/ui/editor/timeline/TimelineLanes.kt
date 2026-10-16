package com.savie.precut.ui.editor.timeline

import com.savie.precut.model.slotStartTimesMs
import androidx.compose.foundation.layout.Box
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
import com.savie.precut.model.lookLayerLanes
import com.savie.precut.model.textLanes
import com.savie.precut.model.stickerLanes
import com.savie.precut.model.effectLanes
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.timelineAt
import com.savie.precut.model.timelineEndMs
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.overlayLanes

// DESIGN.md "Timeline": 2 top padding, 18 ruler, 6 around the 56 main strip, 22/24 rows 4 apart.
internal val TopPadding = 2.dp
internal val BottomPadding = 10.dp
internal val RulerHeight = 18.dp
internal val StripGap = 6.dp
internal val RowGap = 4.dp
internal val ClipHeight = 56.dp
/** Distance from the timeline's top to the main strip, below any overlay rows (none when [compact]). */
internal fun stripTop(project: Project, compact: Boolean): Dp =
    TopPadding + RulerHeight + StripGap + if (compact) 0.dp else overlayRowsHeight(project.overlayLanes().size)
private val TextLaneHeight = 22.dp
private val AudioRowHeight = 24.dp

/**
 * Height the timeline needs: its look, text and audio rows (at least one text and one audio row),
 * or only the ruler and main strip when [compact] (a tall panel is open).
 */
fun timelineHeight(project: Project, compact: Boolean = false): Dp {
    val stripTop = stripTop(project, compact)
    if (compact) return stripTop + ClipHeight + BottomPadding
    val layerRows = project.lookLayerLanes().size
    val textRows = project.textLanes().size.coerceAtLeast(1)
    val stickerRows = project.stickerLanes().size
    val effectRows = project.effectLanes().size
    val audioRows = project.audioLanes().size.coerceAtLeast(1)
    return stripTop + ClipHeight + StripGap +
        (TextLaneHeight + RowGap) * (layerRows + stickerRows + effectRows) +
        TextLaneHeight * textRows + RowGap * (textRows - 1) + (RowGap + AudioRowHeight) * audioRows + BottomPadding
}

/** What's selected on the timeline; at most one of the single selections is set. */
data class TimelineSelection(
    val clipId: String? = null,
    val textId: String? = null,
    val audioId: String? = null,
    val layerId: String? = null,
    /** Items whose media is missing (drawn in red). */
    val missingIds: Set<String> = emptySet(),
    /** Items picked in multi-select mode (highlighted, no handles). */
    val multiIds: Set<String> = emptySet(),
    /** Multi-select is on: unpicked clips show an empty check circle. */
    val multiSelectMode: Boolean = false,
    /** The junction whose transition is being edited: the id of the clip before it. */
    val transitionClipId: String? = null,
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
    /** Only the ruler and main strip (a tall panel is open). */
    compact: Boolean = false,
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
    Spacer(Modifier.height(StripGap))
    if (!compact) {
        OverlayRows(
            lanes = project.overlayLanes(),
            selectedId = selection.clipId,
            videoDurationMs = totalMs,
            pxPerMs = pxPerMs,
            rowHeight = OverlayRowHeight,
            rowGap = StripGap,
            loader = loader,
            snap = snap,
            actions = actions,
        )
    }
    Box {
        ClipStrip(
            project = project,
            selectedClipId = selection.clipId,
            multiIds = selection.multiIds,
            multiSelectMode = selection.multiSelectMode,
            missingIds = selection.missingIds,
            pxPerMs = pxPerMs,
            clipHeight = ClipHeight,
            visiblePx = visiblePx,
            loader = loader,
            snapTargets = trimTargets,
            snapThresholdMs = snapThresholdMs,
            onTrim = actions::onTrim,
        )
        TransitionMarkers(project, selection, pxPerMs, actions::onSelectTransition)
    }
    if (compact) return
    Spacer(Modifier.height(StripGap))
    LookLayerRows(
        lanes = project.lookLayerLanes(),
        selectedLayerId = selection.layerId,
        pxPerMs = pxPerMs,
        widthPx = contentWidthPx,
        rowHeight = TextLaneHeight,
        rowGap = RowGap,
        snap = snap,
        actions = actions,
    )
    TextRows(
        lanes = project.stickerLanes(),
        selectedTextId = selection.textId,
        multiIds = selection.multiIds,
        pxPerMs = pxPerMs,
        widthPx = contentWidthPx,
        rowHeight = TextLaneHeight,
        rowGap = RowGap,
        snap = snap,
        onSelect = actions::onSelectText,
        onTiming = actions::onTextTiming,
        stickers = true,
    )
    EffectLayerRows(
        lanes = project.effectLanes(),
        selectedLayerId = selection.layerId,
        pxPerMs = pxPerMs,
        widthPx = contentWidthPx,
        rowHeight = TextLaneHeight,
        rowGap = RowGap,
        snap = snap,
        actions = actions,
    )
    TextRows(
        lanes = project.textLanes(),
        selectedTextId = selection.textId,
        multiIds = selection.multiIds,
        pxPerMs = pxPerMs,
        widthPx = contentWidthPx,
        rowHeight = TextLaneHeight,
        rowGap = RowGap,
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
        rowGap = RowGap,
        waveforms = waveforms,
        snap = snap,
        actions = actions,
    )
}

/** Edges and beats bars can snap to, tagged with the id of the item they belong to. */
private fun snapTargetsOf(project: Project): List<Pair<String, Long>> = buildList {
    project.slotStartTimesMs().forEach { add("" to it) }
    add("" to project.totalDurationMs())
    project.textOverlays.forEach { add(it.id to it.startMs); add(it.id to it.endMs) }
    project.lookLayers.forEach { add(it.id to it.startMs); add(it.id to it.endMs) }
    project.effectLayers.forEach { add(it.id to it.startMs); add(it.id to it.endMs) }
    project.overlays.forEach { add(it.id to it.timelineStartMs); add(it.id to it.timelineEndMs) }
    project.audioClips.forEach { audio ->
        add(audio.id to audio.timelineStartMs)
        add(audio.id to audio.timelineEndMs())
        audio.beatsMs.forEach { add(audio.id to audio.timelineAt(it)) }
    }
}
