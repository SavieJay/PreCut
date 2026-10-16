package com.savie.precut.ui.editor

import com.savie.precut.media.PreviewController
import com.savie.precut.model.edit.clipShownAt
import com.savie.precut.media.frame.LiveFrames
import com.savie.precut.model.Clip
import com.savie.precut.model.clipById
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.frame.ChromaKey
import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.frame.MaskGeometry
import com.savie.precut.model.frame.MaskShape
import com.savie.precut.model.frame.contentAspect

/**
 * Mask (P-03) and Chroma key (P-04) for the selected clip or overlay. Mask gestures and the
 * feather slider move the preview live through [LiveFrames]; the edit lands on release. Chroma
 * values apply on release (the key is part of the clip's look pass, rebuilt with the preview).
 */
class EditorMask(
    private val host: EditorHost,
    private val live: LiveFrames,
    private val preview: PreviewController,
) {

    /** Opens MASK or CHROMA on the target clip, with the playhead on it. */
    fun open(tool: EditorTool) {
        val clipId = host.targetClipId() ?: return
        host.update {
            it.copy(selectedClipId = clipId, selectedTextId = null, selectedAudioId = null, selectedLayerId = null, activeTool = tool)
        }
        // With no key yet, the eyedropper starts on (P-04).
        if (tool == EditorTool.CHROMA) host.update { it.copy(chromaPicking = it.project.clipById(clipId)?.chroma == null) }
        val range = host.ready?.project?.clipTimelineRange(clipId)
        if (range != null && host.positionMs !in range) host.seekTo(range.first)
        // Marks the clip live, so mask gestures can be followed without rebuilding.
        if (tool in EditorFrame.LIVE_TOOLS) host.refreshPreview()
    }

    // Mask

    /** A shape from the row; null is None. A new shape keeps the current mask's place and feather. */
    fun onShape(shape: MaskShape?) = edit { clip ->
        val current = clip.mask
        val mask = when {
            shape == null -> null
            current == null -> ClipMask.of(shape, clip.contentAspect())
            current.shape == shape -> current
            else -> ClipMask.of(shape, clip.contentAspect()).copy(
                centerX = current.centerX,
                centerY = current.centerY,
                rotationDegrees = current.rotationDegrees,
                feather = current.feather,
                inverted = current.inverted,
            )
        }
        EditAction.SetMask(clip.id, mask)
    }

    fun onMaskStart() = preview.beginLiveRedraw()

    fun onMaskLive(mask: ClipMask) {
        val clip = selected() ?: return
        live.set(LiveFrames.Value(clip.id, clip.fit, clip.transform, clip.opacity, mask))
        preview.redraw()
    }

    /** A gesture or the feather slider ended; null when nothing changed. */
    fun onMaskEnd(mask: ClipMask?) {
        preview.endLiveRedraw()
        if (mask != null) edit { EditAction.SetMask(it.id, mask) }
    }

    fun onFeatherLive(feather: Float) {
        val mask = selected()?.mask ?: return
        preview.beginLiveRedraw()
        onMaskLive(MaskGeometry.feathered(mask, feather))
    }

    fun onFeatherDone(feather: Float) {
        val mask = selected()?.mask ?: return
        onMaskEnd(MaskGeometry.feathered(mask, feather))
    }

    fun onCorners(radius: Float) = edit { clip -> clip.mask?.let { EditAction.SetMask(clip.id, it.copy(cornerRadius = radius)) } }

    fun onInvert() = edit { clip -> clip.mask?.let { EditAction.SetMask(clip.id, it.copy(inverted = !it.inverted)) } }

    /** Puts the current shape back to its starting place and size. */
    fun onMaskReset() = edit { clip -> clip.mask?.let { EditAction.SetMask(clip.id, ClipMask.of(it.shape, clip.contentAspect())) } }

    // Chroma key

    fun onPicking(picking: Boolean) = host.update { it.copy(chromaPicking = picking) }

    /** A colour picked on the preview; the first pick turns the key on. */
    fun onChromaPicked(colorArgb: Int) = edit { clip ->
        EditAction.SetChroma(clip.id, clip.chroma?.copy(colorArgb = colorArgb) ?: ChromaKey(colorArgb))
    }

    fun onIntensity(intensity: Float) = edit { clip -> clip.chroma?.let { EditAction.SetChroma(clip.id, it.copy(intensity = intensity)) } }

    fun onShadow(shadow: Float) = edit { clip -> clip.chroma?.let { EditAction.SetChroma(clip.id, it.copy(shadow = shadow)) } }

    fun onChromaReset() = edit { EditAction.SetChroma(it.id, null) }

    private fun selected(): Clip? {
        val ready = host.ready ?: return null
        return ready.project.clipShownAt(ready.selectedClipId, host.positionMs)
    }

    private fun edit(action: (Clip) -> EditAction?) {
        selected()?.let(action)?.let(host::applyEdit)
    }
}
