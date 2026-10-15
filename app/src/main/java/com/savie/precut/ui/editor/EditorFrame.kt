package com.savie.precut.ui.editor

import com.savie.precut.media.MediaImporter
import com.savie.precut.media.PreviewController
import com.savie.precut.media.frame.LiveFrames
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.clipById
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.edit.BatchEdits
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.mirroredOnScreen
import com.savie.precut.model.edit.rotated
import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.CropRatio
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.frame.FrameGeometry
import com.savie.precut.model.frame.uprightAspect
import kotlinx.coroutines.launch

/** Crop mode's working copy of a clip (T-12b): crop, Ratio chip, mirror and rotation, applied together on ✓. */
data class CropDraft(val clip: Clip) {
    val crop: CropRect get() = clip.crop ?: CropRect.FULL

    /** Pixel width ÷ height the crop must keep, or null when free. */
    val lockedAspect: Float? get() = FrameGeometry.lockedAspect(clip.cropRatio, clip.uprightAspect(), 1f)
}

/**
 * Edit (crop, mirror, rotate), Transform, Opacity and Canvas for the selected clip (or the one
 * under the playhead). While a transform gesture or the opacity slider moves, the preview follows
 * through [LiveFrames]; the edit is committed on release.
 */
class EditorFrame(
    private val host: EditorHost,
    private val live: LiveFrames,
    private val preview: PreviewController,
    private val importer: MediaImporter,
) {

    /** Opens EDIT, TRANSFORM, OPACITY or CANVAS on the target clip. */
    fun open(tool: EditorTool) {
        val clipId = host.targetClipId() ?: return
        host.update {
            it.copy(selectedClipId = clipId, selectedTextId = null, selectedAudioId = null, selectedLayerId = null, activeTool = tool)
        }
        if (tool in LIVE_TOOLS) {
            seekInto(clipId)
            // The clip is marked live so its frame can follow gestures.
            host.refreshPreview()
        }
    }

    fun onMirror() = edit { EditAction.ToggleMirror(it.id) }

    fun onRotate() = edit { EditAction.RotateClip(it.id) }

    // Crop mode

    fun onCropStart() {
        val clip = selected() ?: return
        preview.pause()
        host.update { it.copy(cropDraft = CropDraft(clip.copy(crop = clip.crop ?: CropRect.FULL))) }
    }

    fun onCropChanged(crop: CropRect) = draft { it.copy(crop = crop) }

    fun onCropRatio(ratio: CropRatio) = draft {
        val withRatio = it.copy(cropRatio = ratio)
        withRatio.copy(crop = FrameGeometry.maxCentredCrop(FrameGeometry.lockedAspect(ratio, withRatio.uprightAspect(), 1f), withRatio.uprightAspect(), 1f))
    }

    fun onCropReset() = draft { it.copy(crop = CropRect.FULL, cropRatio = CropRatio.FREE) }

    fun onCropMirror() = draft { it.mirroredOnScreen() }

    fun onCropRotate() = draft { it.rotated() }

    fun onCropCancel() = host.update { it.copy(cropDraft = null) }

    fun onCropApply() {
        val clip = host.ready?.cropDraft?.clip ?: return
        host.update { it.copy(cropDraft = null) }
        host.applyEdit(EditAction.SetCrop(clip.id, clip.crop, clip.cropRatio, clip.mirrored, clip.rotationDegrees))
    }

    // Transform

    fun onTransformStart() {
        if (host.ready?.activeTool != EditorTool.TRANSFORM) open(EditorTool.TRANSFORM)
        preview.beginLiveRedraw()
    }

    fun onTransformLive(transform: ClipTransform) {
        val clip = selected() ?: return
        live.set(LiveFrames.Value(clip.id, clip.fit, transform, clip.opacity))
        preview.redraw()
    }

    fun onTransformEnd(transform: ClipTransform?) {
        preview.endLiveRedraw()
        val clip = selected() ?: return
        if (transform != null) host.applyEdit(EditAction.SetClipTransform(clip.id, clip.fit, transform))
    }

    /** Fit or Fill, starting the transform over. */
    fun onFit(fit: FrameFit) = edit { EditAction.SetClipTransform(it.id, fit, ClipTransform.IDENTITY) }

    fun onResetTransform() = onFit(FrameFit.FIT)

    // Opacity

    fun onOpacityLive(opacity: Float) {
        val clip = selected() ?: return
        preview.beginLiveRedraw()
        live.set(LiveFrames.Value(clip.id, clip.fit, clip.transform, opacity))
        preview.redraw()
    }

    fun onOpacityDone(opacity: Float) {
        preview.endLiveRedraw()
        edit { EditAction.SetOpacity(it.id, opacity) }
    }

    // Canvas

    fun onCanvas(canvas: ClipCanvas) = edit { EditAction.SetCanvas(it.id, canvas) }

    fun onCanvasApplyAll() {
        val ready = host.ready ?: return
        val canvas = selected()?.canvas ?: return
        host.applyEdit(BatchEdits.canvas(ready.project, canvas))
    }

    fun onPickCanvasImage() = host.send(EditorEvent.PickCanvasImage)

    fun onCanvasImagePicked(uri: String?) {
        if (uri == null) return
        host.scope.launch {
            val photo = importer.import(listOf(uri)).imported.firstOrNull { it.info.type == ClipType.IMAGE }
            if (photo == null) {
                host.send(EditorEvent.NothingImported)
            } else {
                onCanvas(ClipCanvas(kind = CanvasKind.IMAGE, imageUri = photo.uri, imageRotationDegrees = photo.info.rotationDegrees))
            }
        }
    }

    private fun selected(): Clip? {
        val ready = host.ready ?: return null
        return ready.project.clipById(ready.selectedClipId)
    }

    private fun edit(action: (Clip) -> EditAction) {
        selected()?.let { host.applyEdit(action(it)) }
    }

    private fun draft(change: (Clip) -> Clip) = host.update { ready ->
        ready.cropDraft?.let { ready.copy(cropDraft = CropDraft(change(it.clip))) } ?: ready
    }

    /** The preview should show the clip being transformed: move the playhead into it if it's elsewhere. */
    private fun seekInto(clipId: String) {
        val range = host.ready?.project?.clipTimelineRange(clipId) ?: return
        if (host.positionMs !in range) host.seekTo(range.first)
    }

    companion object {
        /** Tools whose clip is marked live in the preview. */
        val LIVE_TOOLS = setOf(EditorTool.TRANSFORM, EditorTool.OPACITY)

        /** Tools that keep the clip's transform frame on the preview. */
        val FRAME_TOOLS = setOf(EditorTool.EDIT, EditorTool.TRANSFORM, EditorTool.OPACITY, EditorTool.CANVAS)
    }
}
