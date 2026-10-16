package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.savie.precut.media.PreviewState
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.model.ClipType
import com.savie.precut.model.clipById
import com.savie.precut.model.edit.clipShownAt
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.frame.MaskShape
import com.savie.precut.model.outputSize
import com.savie.precut.model.timelineOffsetToSourceMs
import com.savie.precut.ui.editor.preview.ChromaPickOverlay
import com.savie.precut.ui.editor.preview.MaskGestureActions
import com.savie.precut.ui.editor.preview.MaskOverlay
import com.savie.precut.ui.editor.tools.ChromaActions
import com.savie.precut.ui.editor.tools.MaskActions

/** The mask's outline or the chroma eyedropper on the preview, while their panel is open on the clip under the playhead. */
@Composable
fun MaskPreviewOverlay(state: EditorUiState.Ready, previewState: State<PreviewState>, viewModel: EditorViewModel) {
    val tool = state.activeTool
    if (tool != EditorTool.MASK && tool != EditorTool.CHROMA) return
    val stored = state.project.clipById(state.selectedClipId) ?: return
    // Only keyframed clips follow the playhead (reading it recomposes on every tick).
    val clip = if (stored.keyframes.isEmpty()) stored else state.project.clipShownAt(stored.id, previewState.value.positionMs) ?: stored
    val range = remember(state.project, clip.id) { state.project.clipTimelineRange(clip.id) ?: LongRange.EMPTY }
    val onClip by remember(range) { derivedStateOf { previewState.value.positionMs in range } }
    if (!onClip) return
    val output = state.project.outputSize()
    if (tool == EditorTool.MASK) {
        val mask = clip.mask ?: return
        val actions = remember(viewModel) {
            object : MaskGestureActions {
                override fun onStart() = viewModel.mask.onMaskStart()
                override fun onLive(mask: ClipMask) = viewModel.mask.onMaskLive(mask)
                override fun onEnd(mask: ClipMask?) = viewModel.mask.onMaskEnd(mask)
            }
        }
        MaskOverlay(clip, mask, output, actions)
    } else if (state.chromaPicking) {
        // The exact source frame under the playhead, upright, to pick colours from.
        val sourceMs = if (clip.type == ClipType.IMAGE) 0L else clip.timelineOffsetToSourceMs(previewState.value.positionMs - range.first)
        val rotation = if (clip.type == ClipType.IMAGE) clip.sourceRotationDegrees else 0
        val key = ThumbnailKey(clip.uri, clip.type, sourceMs, PICK_FRAME_HEIGHT_PX, rotation, exact = true)
        val frame by produceState<android.graphics.Bitmap?>(null, key) { value = viewModel.thumbnails.load(key, cache = false) }
        ChromaPickOverlay(clip, frame, output, onPicked = viewModel.mask::onChromaPicked)
    }
}

@Composable
fun rememberMaskActions(viewModel: EditorViewModel): MaskActions = remember(viewModel) {
    val mask = viewModel.mask
    object : MaskActions {
        override fun onShape(shape: MaskShape?) = mask.onShape(shape)
        override fun onFeatherLive(feather: Float) = mask.onFeatherLive(feather)
        override fun onFeatherDone(feather: Float) = mask.onFeatherDone(feather)
        override fun onCorners(radius: Float) = mask.onCorners(radius)
        override fun onInvert() = mask.onInvert()
        override fun onReset() = mask.onMaskReset()
    }
}

@Composable
fun rememberChromaActions(viewModel: EditorViewModel): ChromaActions = remember(viewModel) {
    val mask = viewModel.mask
    object : ChromaActions {
        override fun onPicking(picking: Boolean) = mask.onPicking(picking)
        override fun onIntensity(intensity: Float) = mask.onIntensity(intensity)
        override fun onShadow(shadow: Float) = mask.onShadow(shadow)
        override fun onReset() = mask.onChromaReset()
    }
}

private const val PICK_FRAME_HEIGHT_PX = 540
