package com.savie.precut.ui.editor

import android.os.SystemClock
import com.savie.precut.media.PreviewController
import com.savie.precut.media.cutout.MatteMaker
import com.savie.precut.media.cutout.MatteResult
import com.savie.precut.model.Clip
import com.savie.precut.model.clipById
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.frame.Cutout
import com.savie.precut.model.timelineDurationMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Remove background (Phase 22A) on the selected clip or overlay: makes its matte on the phone
 * (progress and Cancel, as Reverse), then turns the cutout on in one undo step. Softness and Edge
 * apply on release (the cutout is part of the clip's look pass, rebuilt with the preview); Show
 * original is the before/after peek.
 */
class EditorCutout(
    private val host: EditorHost,
    private val preview: PreviewController,
    private val maker: MatteMaker,
) {
    private var job: Job? = null

    /** Opens the panel on the target clip, with the playhead on it. */
    fun open() {
        val clipId = host.targetClipId() ?: return
        host.update {
            it.copy(selectedClipId = clipId, selectedTextId = null, selectedAudioId = null, selectedLayerId = null, activeTool = EditorTool.CUTOUT)
        }
        val range = host.ready?.project?.clipTimelineRange(clipId)
        if (range != null && host.positionMs !in range) host.seekTo(range.first)
    }

    /** The switch: on makes the matte (instant when one was made before), off removes the cutout. */
    fun onEnabled(enabled: Boolean) {
        val clip = selected() ?: return
        if (!enabled) {
            stopPeeking()
            host.applyEdit(EditAction.SetCutout(clip.id, null))
        } else if (clip.cutout == null) {
            makeMatte(clip)
        }
    }

    /** After a longer trim or a Reverse, a matte covering what the clip shows now (Softness and Edge kept). */
    fun onUpdate() {
        selected()?.let(::makeMatte)
    }

    fun onSoftness(softness: Float) = edit { it.copy(softness = softness) }

    fun onEdge(edge: Float) = edit { it.copy(edge = edge) }

    /** Before/after: the preview shows the clip without its cutout until toggled back (not saved). */
    fun onShowOriginal() {
        host.update { it.copy(peekOriginal = !it.peekOriginal) }
        host.refreshPreview()
    }

    fun onCancel() {
        job?.cancel()
    }

    private fun makeMatte(clip: Clip) {
        if (job?.isActive == true) return
        preview.pause()
        stopPeeking()
        host.update { it.copy(cuttingOut = ReverseProgress(clip.timelineDurationMs(), 0, null)) }
        val started = SystemClock.elapsedRealtime()
        job = host.scope.launch {
            try {
                val result = maker.make(clip) { fraction ->
                    val percent = (fraction * 100).roundToInt().coerceIn(0, 99)
                    val elapsed = SystemClock.elapsedRealtime() - started
                    val left = if (fraction > ESTIMATE_AFTER) ((elapsed / fraction - elapsed) / 1_000).roundToInt() else null
                    host.update { ready -> ready.cuttingOut?.takeIf { it.percent != percent || it.secondsLeft != left }?.let { ready.copy(cuttingOut = it.copy(percent = percent, secondsLeft = left)) } ?: ready }
                }
                when (result) {
                    is MatteResult.Done -> {
                        host.applyEdit(EditAction.SetCutout(clip.id, result.cutout))
                        host.send(EditorEvent.CutoutDone)
                    }
                    MatteResult.NoPerson -> host.send(EditorEvent.CutoutNoPerson)
                    is MatteResult.Failed -> host.send(EditorEvent.CutoutFailed)
                }
            } finally {
                host.update { it.copy(cuttingOut = null) }
            }
        }
    }

    private fun edit(change: (Cutout) -> Cutout) {
        val clip = selected() ?: return
        val cutout = clip.cutout ?: return
        stopPeeking()
        host.applyEdit(EditAction.SetCutout(clip.id, change(cutout)))
    }

    private fun stopPeeking() {
        if (host.ready?.peekOriginal == true) host.update { it.copy(peekOriginal = false) }
    }

    private fun selected(): Clip? = host.ready?.let { it.project.clipById(it.selectedClipId) }

    private companion object {
        /** Estimates are guesses before this much progress. */
        const val ESTIMATE_AFTER = 0.05f
    }
}
