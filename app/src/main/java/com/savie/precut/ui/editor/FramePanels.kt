package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.ui.editor.tools.CanvasActions
import com.savie.precut.ui.editor.tools.CanvasTool
import com.savie.precut.ui.editor.tools.EditTool
import com.savie.precut.ui.editor.tools.OpacityTool
import com.savie.precut.ui.editor.tools.TransformTool

/** The Edit, Transform, Opacity and Canvas panels for [clip]. */
@Composable
fun FramePanels(clip: Clip, tool: EditorTool, viewModel: EditorViewModel) {
    val frame = viewModel.frame
    val close = viewModel::onToolClosed
    when (tool) {
        EditorTool.EDIT -> EditTool(onCrop = frame::onCropStart, onMirror = frame::onMirror, onRotate = frame::onRotate, onDone = close)
        EditorTool.TRANSFORM -> TransformTool(clip.fit, clip.transform, onFit = frame::onFit, onReset = frame::onResetTransform, onDone = close)
        EditorTool.OPACITY -> OpacityTool(clip.opacity, onLive = frame::onOpacityLive, onChanged = frame::onOpacityDone, onDone = close)
        EditorTool.CANVAS -> {
            val actions = remember(frame) {
                object : CanvasActions {
                    override fun onCanvas(canvas: ClipCanvas) = frame.onCanvas(canvas)
                    override fun onApplyAll() = frame.onCanvasApplyAll()
                    override fun onPickImage() = frame.onPickCanvasImage()
                }
            }
            val key = remember(clip.uri, clip.trimStartMs) {
                ThumbnailKey(clip.uri, clip.type, clip.trimStartMs, TILE_FRAME_HEIGHT_PX, if (clip.type == ClipType.IMAGE) clip.sourceRotationDegrees else 0)
            }
            CanvasTool(clip.canvas, key, viewModel.thumbnails, actions, onDone = close)
        }
        else -> Unit
    }
}

private const val TILE_FRAME_HEIGHT_PX = 160
