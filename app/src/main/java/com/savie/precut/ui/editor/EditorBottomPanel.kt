package com.savie.precut.ui.editor

import android.Manifest
import com.savie.precut.model.edit.clipShownAt
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.edit.MIN_CLIP_DURATION_MS
import com.savie.precut.model.clipById
import com.savie.precut.model.coverSource
import com.savie.precut.model.overlayById
import com.savie.precut.model.frameRatio
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.ui.editor.tools.AudioMenuBar
import com.savie.precut.ui.editor.tools.ChromaTool
import com.savie.precut.ui.editor.tools.ClipVolumeTool
import com.savie.precut.ui.editor.tools.MaskTool
import com.savie.precut.ui.editor.tools.CoverTool
import com.savie.precut.ui.editor.tools.FadeTool
import com.savie.precut.ui.editor.tools.KeyframeEasingTool
import com.savie.precut.ui.editor.tools.LayerToolbar
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
fun EditorBottomPanel(state: EditorUiState.Ready, canSplit: Boolean, positionMs: () -> Long, viewModel: EditorViewModel) {
    val context = LocalContext.current
    val recording by viewModel.audio.recording.collectAsStateWithLifecycle()
    // With keyframes, panels show (and edit from) the values at the playhead.
    val clip = state.project.clipShownAt(state.selectedClipId, positionMs())
    val text = state.project.textOverlays.firstOrNull { it.id == state.selectedTextId }
    val audio = state.project.audioClips.firstOrNull { it.id == state.selectedAudioId }
    val close = viewModel::onToolClosed

    state.multiSelect?.let { picked ->
        SelectionPanels(state, picked, viewModel)
        return
    }
    if (audio != null) {
        AudioPanels(audio, state.activeTool, positionMs, viewModel)
        return
    }
    state.activeTool?.takeIf { it in EFFECT_TOOLS }?.let { tool ->
        EffectPanels(state, tool, positionMs, viewModel)
        return
    }
    state.project.effectLayers.firstOrNull { it.id == state.selectedLayerId }?.let { layer ->
        EffectLayerPanel(layer, positionMs, viewModel)
        return
    }
    state.project.lookLayers.firstOrNull { it.id == state.selectedLayerId }?.let { layer ->
        val tool = state.activeTool
        if (tool in LOOK_TOOLS && tool != null) {
            LookPanels(state, layer.look, tool, viewModel)
        } else {
            LayerToolbar(
                canSplit = rememberCanSplit(positionMs, layer.startMs, layer.endMs),
                actions = rememberLayerToolbarActions(viewModel),
            )
        }
        return
    }
    state.activeTool?.takeIf { it in STICKER_SHEET_TOOLS }?.let { tool ->
        StickerSheetPanels(tool, positionMs, viewModel)
        return
    }
    if (text?.sticker != null && state.activeTool != EditorTool.KEYFRAME_EASING) {
        SelectedStickerPanels(state, text, positionMs, viewModel)
        return
    }
    if (state.activeTool == EditorTool.KEYFRAME_EASING) {
        KeyframeEasingPanel(state, positionMs, viewModel)
        return
    }
    when {
        state.activeTool == EditorTool.TRANSITION && state.selectedTransitionId != null ->
            TransitionPanel(state, state.selectedTransitionId, viewModel)
        state.activeTool == EditorTool.ANIMATION && clip != null -> AnimationPanel(clip, viewModel)
        state.activeTool == EditorTool.TRIM && clip != null ->
            TrimTool(clip, viewModel.thumbnails, onTrim = { start, end -> viewModel.onTrim(clip.id, start, end) }, onDone = close)
        state.activeTool == EditorTool.SPEED && clip != null ->
            SpeedTool(
                clip.speed, onSpeedChanged = viewModel::onSpeedChanged, onDone = close,
                sourceLengthMs = clip.trimEndMs - clip.trimStartMs,
                keepPitch = clip.keepPitch, onKeepPitchChanged = viewModel::onKeepPitchChanged,
                showCurveTab = true,
                curve = clip.speedCurve,
                onCurve = viewModel::onSpeedCurveChanged,
            )
        state.activeTool in LOOK_TOOLS && clip != null && state.activeTool != null ->
            LookPanels(state, clip.look, state.activeTool, viewModel)
        state.activeTool == EditorTool.VOLUME && clip != null -> ClipVolumeTool(
            clip, viewModel::onClipVolumeChanged, viewModel::onClipMutedChanged, onDone = close,
            onApplyAll = viewModel::onVolumeApplyAll,
        )
        state.activeTool in EditorFrame.FRAME_TOOLS && clip != null && state.activeTool != null ->
            FramePanels(clip, state.activeTool, viewModel)
        state.activeTool == EditorTool.MASK && clip != null -> MaskTool(clip.mask, rememberMaskActions(viewModel), onDone = close)
        state.activeTool == EditorTool.CHROMA && clip != null ->
            ChromaTool(clip.chroma, state.chromaPicking, rememberChromaActions(viewModel), onDone = close)
        state.activeTool == EditorTool.FADE && clip != null ->
            FadeTool(clip.fadeInMs, clip.fadeOutMs, clip.timelineDurationMs(), viewModel::onClipFadesChanged, onDone = close)
        state.activeTool in TEXT_STYLE_TOOLS && text != null && state.activeTool != null ->
            TextStylePanels(state, text, state.activeTool, viewModel)
        state.activeTool == EditorTool.TEXT && text != null ->
            TextTool(
                overlay = text,
                canSplit = rememberCanSplit(positionMs, text.startMs, text.endMs),
                recentColors = state.project.textOverlays.map { it.colorArgb }.distinct(),
                actions = rememberTextToolActions(viewModel),
                onDone = close,
            )
        state.activeTool == EditorTool.RATIO -> RatioTool(
            state.project.aspectRatio, state.project.copy(aspectRatio = AspectRatio.ORIGINAL).frameRatio(),
            onSelected = viewModel::onAspectRatioSelected, onDone = close,
        )
        state.activeTool == EditorTool.COVER -> CoverTool(
            cover = state.project.cover,
            inExport = state.project.coverInExport,
            playheadMs = positionMs(),
            coverKey = state.project.coverSource()?.let { ThumbnailKey(it.uri, it.type, it.sourceMs, 256, it.rotationDegrees, exact = true) },
            loader = viewModel.thumbnails,
            actions = rememberCoverActions(viewModel),
            onDone = close,
        )
        state.activeTool == EditorTool.VOICEOVER -> VoiceoverTool(
            recording = recording,
            playheadMs = positionMs(),
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
        state.project.overlayById(state.selectedClipId) != null -> OverlayToolbarFor(viewModel)
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
/** The panels that edit a [com.savie.precut.model.look.ClipLook]. */
val LOOK_TOOLS = setOf(EditorTool.FILTER, EditorTool.ADJUST, EditorTool.HSL, EditorTool.CURVES)

/** [canSplitAt] that only recomposes its reader when the answer changes, not as time moves. */
@Composable
fun rememberCanSplit(positionMs: () -> Long, startMs: Long, endMs: Long): Boolean {
    val canSplit by remember(startMs, endMs) { derivedStateOf { canSplitAt(positionMs(), startMs, endMs) } }
    return canSplit
}

fun canSplitAt(positionMs: Long, startMs: Long, endMs: Long): Boolean =
    positionMs > startMs + MIN_CLIP_DURATION_MS && positionMs < endMs - MIN_CLIP_DURATION_MS

/** The easing panel for the keyframe at the playhead; closes itself when the playhead leaves it. */
@Composable
private fun KeyframeEasingPanel(state: EditorUiState.Ready, positionMs: () -> Long, viewModel: EditorViewModel) {
    val easing = viewModel.keyframes.easing(state, positionMs())
    if (easing == null) {
        LaunchedEffect(Unit) { viewModel.onToolClosed() }
        return
    }
    KeyframeEasingTool(easing.easing, easing.curve, onEasing = viewModel.keyframes::onEasing, onDone = viewModel::onToolClosed)
}
