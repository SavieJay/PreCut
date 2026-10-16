package com.savie.precut.ui.editor

import com.savie.precut.model.clipById
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.keyframeAt
import com.savie.precut.model.keyframe.Easing
import com.savie.precut.model.keyframe.EasingCurve

/** The keyframe button next to Play (P-05): shown while a clip or text is selected and under the playhead. */
data class KeyframeButton(
    /** A keyframe sits at the playhead: the button removes it (and offers its easing). */
    val atKeyframe: Boolean,
)

/** The easing of the keyframe at the playhead, for the easing panel. */
data class KeyframeEasingState(val easing: Easing, val curve: EasingCurve?)

/** Keyframes (P-05) on the selected clip, overlay or text: add or remove at the playhead, and easing. */
class EditorKeyframes(private val host: EditorHost) {

    /** The button for [ready]'s selection at [positionMs]; null when it shouldn't show. */
    fun button(ready: EditorUiState.Ready, positionMs: Long): KeyframeButton? {
        ready.selectedTextId?.let { id ->
            val text = ready.project.textOverlays.firstOrNull { it.id == id } ?: return null
            if (positionMs !in text.startMs..text.endMs) return null
            return KeyframeButton(text.keyframeAt(positionMs - text.startMs) != null)
        }
        val clip = ready.project.clipById(ready.selectedClipId) ?: return null
        val range = ready.project.clipTimelineRange(clip.id) ?: return null
        if (positionMs !in range.first..range.last + 1) return null
        return KeyframeButton(clip.keyframeAt(positionMs - range.first) != null)
    }

    /** Adds a keyframe of the current values at the playhead, or removes the one there. */
    fun onToggle() {
        val ready = host.ready ?: return
        val position = host.positionMs
        val state = button(ready, position) ?: return
        val textId = ready.selectedTextId
        val action = when {
            textId != null && state.atKeyframe -> EditAction.RemoveTextKeyframe(textId, position)
            textId != null -> EditAction.AddTextKeyframe(textId, position)
            state.atKeyframe -> EditAction.RemoveKeyframe(ready.selectedClipId ?: return, position)
            else -> EditAction.AddKeyframe(ready.selectedClipId ?: return, position)
        }
        host.applyEdit(action)
    }

    /** Opens the easing panel when the playhead is on a keyframe. */
    fun onOpenEasing() {
        val ready = host.ready ?: return
        if (button(ready, host.positionMs)?.atKeyframe != true) return
        host.update { it.copy(activeTool = EditorTool.KEYFRAME_EASING) }
    }

    /** The easing out of the keyframe at the playhead, if there is one. */
    fun easing(ready: EditorUiState.Ready, positionMs: Long): KeyframeEasingState? {
        ready.selectedTextId?.let { id ->
            val text = ready.project.textOverlays.firstOrNull { it.id == id } ?: return null
            val keyframe = text.keyframeAt(positionMs - text.startMs) ?: return null
            return KeyframeEasingState(keyframe.easing, keyframe.curve)
        }
        val clip = ready.project.clipById(ready.selectedClipId) ?: return null
        val range = ready.project.clipTimelineRange(clip.id) ?: return null
        val keyframe = clip.keyframeAt(positionMs - range.first) ?: return null
        return KeyframeEasingState(keyframe.easing, keyframe.curve)
    }

    fun onEasing(easing: Easing, curve: EasingCurve?) {
        val ready = host.ready ?: return
        val position = host.positionMs
        val textId = ready.selectedTextId
        val action = if (textId != null) {
            EditAction.SetTextKeyframeEasing(textId, position, easing, curve)
        } else {
            EditAction.SetKeyframeEasing(ready.selectedClipId ?: return, position, easing, curve)
        }
        host.applyEdit(action)
    }
}
