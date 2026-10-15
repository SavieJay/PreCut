package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.savie.precut.media.PreviewState
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.model.clipById
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.CropRatio
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.outputSize
import com.savie.precut.model.timelineOffsetToSourceMs
import com.savie.precut.ui.editor.crop.CropActions
import com.savie.precut.ui.editor.crop.CropMode
import com.savie.precut.ui.editor.preview.ClipTransformActions
import com.savie.precut.ui.editor.preview.ClipTransformOverlay

/**
 * The selected main clip's transform frame on the preview (T-15), while the playhead is on that
 * clip and no other tool needs the preview.
 */
@Composable
fun ClipFrameOverlay(state: EditorUiState.Ready, previewState: State<PreviewState>, viewModel: EditorViewModel) {
    val clip = state.project.clipById(state.selectedClipId) ?: return
    val toolAllows = state.activeTool == null || state.activeTool in EditorFrame.FRAME_TOOLS
    if (!toolAllows || state.selectedTextId != null || state.multiSelect != null) return
    val range = remember(state.project, clip.id) { state.project.clipTimelineRange(clip.id) ?: LongRange.EMPTY }
    val onClip by remember(range) { derivedStateOf { previewState.value.positionMs in range } }
    if (!onClip) return
    val actions = remember(viewModel) {
        object : ClipTransformActions {
            override fun onStart() = viewModel.frame.onTransformStart()
            override fun onLive(transform: ClipTransform) = viewModel.frame.onTransformLive(transform)
            override fun onEnd(transform: ClipTransform?) = viewModel.frame.onTransformEnd(transform)
        }
    }
    ClipTransformOverlay(clip, state.project.outputSize(), actions)
}

/** Crop mode for [draft], showing the frame under the playhead (or the clip's first frame). */
@Composable
fun EditorCropMode(state: EditorUiState.Ready, draft: CropDraft, positionMs: () -> Long, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    val key = remember(draft.clip.id, draft.clip.uri) { cropFrameKey(state.project, draft.clip, positionMs()) }
    val actions = remember(viewModel) {
        val frame = viewModel.frame
        object : CropActions {
            override fun onChange(crop: CropRect) = frame.onCropChanged(crop)
            override fun onRatio(ratio: CropRatio) = frame.onCropRatio(ratio)
            override fun onReset() = frame.onCropReset()
            override fun onMirror() = frame.onCropMirror()
            override fun onRotate() = frame.onCropRotate()
            override fun onCancel() = frame.onCropCancel()
            override fun onApply() = frame.onCropApply()
        }
    }
    CropMode(draft, key, viewModel.thumbnails, actions, modifier)
}

/** The exact source frame to crop on: the one under the playhead when it's on [clip]. */
private fun cropFrameKey(project: Project, clip: Clip, positionMs: Long): ThumbnailKey {
    val range = project.clipTimelineRange(clip.id) ?: LongRange.EMPTY
    val sourceMs = when {
        clip.type == ClipType.IMAGE -> 0L
        positionMs in range -> clip.timelineOffsetToSourceMs(positionMs - range.first)
        else -> clip.trimStartMs
    }
    val rotation = if (clip.type == ClipType.IMAGE) clip.sourceRotationDegrees else 0
    return ThumbnailKey(clip.uri, clip.type, sourceMs, CROP_FRAME_HEIGHT_PX, rotation, exact = true)
}

private const val CROP_FRAME_HEIGHT_PX = 1080
