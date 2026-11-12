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

// RD-E03 redline 6: a 22 ruler, the 68 main strip, 30 text/layer rows and 40 audio rows, 6 apart.
internal val TopPadding = 0.dp
internal val BottomPadding = 8.dp
internal val RulerHeight = 22.dp
internal val StripGap = 6.dp
internal val RowGap = 6.dp
internal val ClipHeight = 68.dp
/**
 * Which lanes the timeline shows: all of them, only the ruler and main strip (a tall panel is
 * open), or the strip with the sticker and text rows (a text panel is open, Phase 29E: the text's
 * bar stays visible and draggable above its panel).
 */
enum class TimelineFocus { All, Strip, StripAndText }

/** Distance from the timeline's top to the main strip, below any overlay rows (shown only for [TimelineFocus.All]). */
internal fun stripTop(project: Project, focus: TimelineFocus): Dp =
    TopPadding + RulerHeight + StripGap + if (focus != TimelineFocus.All) 0.dp else overlayRowsHeight(project.overlayLanes().size)
internal val TextLaneHeight = 30.dp
internal val AudioRowHeight = 40.dp

/**
 * Height the timeline needs for [focus]: its look, text and audio rows (at least one text and one
 * audio row), only the ruler and main strip, or the strip with the sticker and text rows.
 */
fun timelineHeight(project: Project, focus: TimelineFocus = TimelineFocus.All): Dp {
    val stripTop = stripTop(project, focus)
    if (focus == TimelineFocus.Strip) return stripTop + ClipHeight + BottomPadding
    val textRows = project.textLanes().size.coerceAtLeast(1)
    val stickerRows = project.stickerLanes().size
    if (focus == TimelineFocus.StripAndText) {
        return stripTop + ClipHeight + StripGap + (TextLaneHeight + RowGap) * stickerRows +
            TextLaneHeight * textRows + RowGap * (textRows - 1) + BottomPadding
    }
    val layerRows = project.lookLayerLanes().size
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
    /** Which lanes show (a tall panel or a text panel is open). */
    focus: TimelineFocus = TimelineFocus.All,
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
    if (focus == TimelineFocus.All) {
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
    if (focus == TimelineFocus.Strip) return
    Spacer(Modifier.height(StripGap))
    val all = focus == TimelineFocus.All
    if (all) {
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
    }
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
    if (all) {
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
    }
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
    if (!all) return
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
