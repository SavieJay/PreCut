package com.savie.precut.ui.editor

import com.savie.precut.media.FreezeFrames
import com.savie.precut.media.PreviewController
import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.clipAt
import com.savie.precut.model.clipById
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.motion.DEFAULT_TRANSITION_MS
import com.savie.precut.model.motion.MotionMath
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineOffsetToSourceMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which animation slot the Animation panel edits. */
enum class AnimationSlot { IN, OUT, COMBO }

/**
 * Transitions (junction markers), Animation and Freeze frame. Picking a transition or animation
 * plays that moment once in the preview, like CapCut's tiles.
 */
class EditorMotion(
    private val host: EditorHost,
    private val preview: PreviewController,
    private val freezeFrames: FreezeFrames,
) {

    private var previewJob: Job? = null

    // Transitions

    /** Opens the Transition panel for the junction after [clipId]. */
    fun onSelectTransition(clipId: String) {
        host.update {
            it.copy(
                selectedTransitionId = clipId, selectedClipId = null, selectedTextId = null, selectedAudioId = null,
                selectedLayerId = null, audioMenuOpen = false, activeTool = EditorTool.TRANSITION,
            )
        }
    }

    fun onTransition(type: TransitionType?) {
        val clipId = host.ready?.selectedTransitionId ?: return
        val current = transitionOf(clipId)
        host.applyEdit(EditAction.SetTransition(clipId, type?.let { ClipTransition(it, current?.durationMs ?: DEFAULT_TRANSITION_MS) }))
        if (type != null) playJunction(clipId)
    }

    fun onTransitionDuration(durationMs: Long) {
        val clipId = host.ready?.selectedTransitionId ?: return
        val current = transitionOf(clipId) ?: return
        host.applyEdit(EditAction.SetTransition(clipId, current.copy(durationMs = durationMs)))
    }

    fun onTransitionApplyAll() {
        val clipId = host.ready?.selectedTransitionId ?: return
        host.applyEdit(EditAction.SetAllTransitions(transitionOf(clipId)))
    }

    // Animations

    fun onAnimation(slot: AnimationSlot, animationIn: ClipAnimation?, animationOut: ClipAnimation?, combo: ComboAnimation?) {
        val ready = host.ready ?: return
        val clipId = ready.selectedClipId ?: return
        host.applyEdit(EditAction.SetAnimations(clipId, animationIn, animationOut, combo))
        val clip = ready.project.clipById(clipId) ?: return
        val start = clipStart(clipId) ?: return
        val length = clip.timelineDurationMs()
        when (slot) {
            AnimationSlot.IN -> animationIn?.let { play(start, it.durationMs.coerceAtMost(length)) }
            AnimationSlot.OUT -> animationOut?.let { val d = it.durationMs.coerceAtMost(length); play(start + length - d, d) }
            AnimationSlot.COMBO -> combo?.let { play(start, MotionMath.COMBO_PERIOD_MS.coerceAtMost(length)) }
        }
    }

    // Freeze frame

    /** Freezes the frame under the playhead of the clip there (videos only), as a 3 s photo. */
    fun onFreeze() {
        val ready = host.ready ?: return
        val at = host.positionMs
        val position = ready.project.clipAt(at) ?: return
        val clip = ready.project.clips[position.index]
        if (clip.type != ClipType.VIDEO) {
            host.send(EditorEvent.FreezeNotForPhotos)
            return
        }
        preview.pause()
        host.scope.launch {
            val frame = freezeFrames.freeze(clip, clip.timelineOffsetToSourceMs(position.offsetMs))
            if (frame == null) {
                host.send(EditorEvent.FreezeFailed)
                return@launch
            }
            val still = clipFromMedia(host.newId(), frame.uri, MediaInfo(ClipType.IMAGE, 0L, frame.width, frame.height, 0))
                .copy(trimEndMs = FREEZE_DURATION_MS)
            host.applyEdit(EditAction.FreezeFrame(at, host.newId(), still))
            host.update { it.copy(selectedClipId = still.id, activeTool = null) }
            host.send(EditorEvent.FrozeFrame)
        }
    }

    private fun transitionOf(clipId: String): ClipTransition? =
        host.ready?.project?.clips?.firstOrNull { it.id == clipId }?.transitionOut

    private fun clipStart(clipId: String): Long? = host.ready?.project?.clipTimelineRange(clipId)?.first

    /** Plays across the junction after [clipId], once the rebuilt preview has the new transition. */
    private fun playJunction(clipId: String) {
        val project = host.ready?.project ?: return
        val index = project.clips.indexOfFirst { it.id == clipId }
        if (index < 0 || index >= project.clips.lastIndex) return
        val junction = project.clipStartTimesMs()[index + 1]
        val duration = transitionOf(clipId)?.durationMs ?: return
        play((junction - duration / 2 - LEAD_MS).coerceAtLeast(0L), duration + 2 * LEAD_MS)
    }

    /** Plays [lengthMs] from [startMs], then pauses (after the edit's preview rebuild settles). */
    private fun play(startMs: Long, lengthMs: Long) {
        previewJob?.cancel()
        previewJob = host.scope.launch {
            delay(REBUILD_SETTLE_MS)
            host.seekTo(startMs)
            preview.play()
            delay(lengthMs)
            preview.pause()
        }
    }

    private companion object {
        const val FREEZE_DURATION_MS = 3_000L
        /** A little before and after a transition, so its whole shape shows. */
        const val LEAD_MS = 300L
        /** The ViewModel rebuilds the preview 150 ms after an edit; play once that's done. */
        const val REBUILD_SETTLE_MS = 400L
    }
}
