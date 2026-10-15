package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.model.clipById
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.model.Project
import com.savie.precut.model.clipAt
import com.savie.precut.model.look.Adjustment
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.timelineOffsetToSourceMs
import com.savie.precut.ui.editor.tools.AdjustActions
import com.savie.precut.ui.editor.tools.AdjustTool
import com.savie.precut.ui.editor.tools.CurvesPanel
import com.savie.precut.ui.editor.tools.FilterActions
import com.savie.precut.ui.editor.tools.FilterTool
import com.savie.precut.ui.editor.tools.HslPanel

/**
 * The Filter, Adjust, HSL or Curves panel for the look being edited (a clip's or a layer's).
 * "Apply to all" is offered for clips only.
 */
@Composable
fun LookPanels(state: EditorUiState.Ready, look: ClipLook, tool: EditorTool, viewModel: EditorViewModel) {
    val controller = viewModel.look
    val close = viewModel::onToolClosed
    val forClip = state.selectedLayerId == null
    when (tool) {
        EditorTool.FILTER -> {
            val customLuts by controller.customLuts.collectAsStateWithLifecycle()
            FilterTool(
                look = look,
                baseKey = remember(state.project, state.selectedClipId, state.selectedLayerId) { previewFrameKey(state) },
                loader = viewModel.thumbnails,
                thumbnailer = viewModel.lookThumbnails,
                customLuts = customLuts,
                actions = remember(viewModel) {
                    object : FilterActions {
                        override fun onFilter(filterId: String?) = controller.onFilter(filterId)
                        override fun onIntensity(intensity: Float) = controller.onIntensity(intensity)
                        override fun onImportLut() = viewModel.onPickLut()
                        override fun onPeek() = controller.onPeek()
                    }
                },
                onDone = close,
                onApplyAll = if (forClip) ({ controller.onApplyAll(filterOnly = true) }) else null,
            )
        }
        EditorTool.HSL -> HslPanel(look.hsl, controller::onHsl, onBack = { viewModel.onToolSelected(EditorTool.ADJUST) }, onDone = close)
        EditorTool.CURVES -> CurvesPanel(look.curves, controller::onCurve, onBack = { viewModel.onToolSelected(EditorTool.ADJUST) }, onDone = close)
        else -> AdjustTool(
            look = look,
            actions = remember(viewModel) {
                object : AdjustActions {
                    override fun onAdjust(adjustment: Adjustment, value: Float) = controller.onAdjust(adjustment, value)
                    override fun onReset() = controller.onResetAdjust()
                    override fun onHsl() = viewModel.onToolSelected(EditorTool.HSL)
                    override fun onCurves() = viewModel.onToolSelected(EditorTool.CURVES)
                }
            },
            onDone = close,
            onApplyAll = if (forClip) ({ controller.onApplyAll(filterOnly = false) }) else null,
        )
    }
}

/** The frame the filter tiles show: the selected clip's first frame, or the frame at a layer's start. */
private fun previewFrameKey(state: EditorUiState.Ready): ThumbnailKey? {
    val project: Project = state.project
    val layer = project.lookLayers.firstOrNull { it.id == state.selectedLayerId }
    val (clip, sourceMs) = if (layer != null) {
        val position = project.clipAt(layer.startMs) ?: return null
        project.clips[position.index].let { it to it.timelineOffsetToSourceMs(position.offsetMs) }
    } else {
        val clip = project.clipById(state.selectedClipId) ?: return null
        clip to clip.trimStartMs
    }
    return ThumbnailKey(clip.uri, clip.type, sourceMs, TILE_THUMBNAIL_PX, clip.sourceRotationDegrees)
}

private const val TILE_THUMBNAIL_PX = 180
