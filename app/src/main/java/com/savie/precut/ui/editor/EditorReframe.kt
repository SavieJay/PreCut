package com.savie.precut.ui.editor

import android.os.SystemClock
import com.savie.precut.media.PreviewController
import com.savie.precut.media.cutout.SubjectTracker
import com.savie.precut.media.cutout.TrackResult
import com.savie.precut.model.Clip
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.canReframe
import com.savie.precut.model.edit.frameAspect
import com.savie.precut.model.frame.ReframeSpeed
import com.savie.precut.model.timelineDurationMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Auto reframe in progress over [clips] clips ([clipLengthMs] long when it's one); [secondsLeft] is null until there's enough to estimate. */
data class ReframeProgress(val clips: Int, val clipLengthMs: Long, val percent: Int, val secondsLeft: Int?)

/**
 * Auto reframe (Phase 22B) on the selected main-track clip, or every one with Apply to all: finds
 * the person on the phone (progress and Cancel), then fills the clip into the frame and keyframes
 * its position, in one undo step. Show original framing is the before/after peek.
 */
class EditorReframe(
    private val host: EditorHost,
    private val preview: PreviewController,
    private val tracker: SubjectTracker,
) {
    private var job: Job? = null

    /** Opens the panel on the target main-track clip, with the playhead on it. */
    fun open() {
        val clipId = host.targetClipId() ?: return
        val ready = host.ready ?: return
        if (ready.project.clips.none { it.id == clipId }) return
        host.update {
            it.copy(selectedClipId = clipId, selectedTextId = null, selectedAudioId = null, selectedLayerId = null, activeTool = EditorTool.REFRAME)
        }
        val range = ready.project.clipTimelineRange(clipId)
        if (range != null && host.positionMs !in range) host.seekTo(range.first)
    }

    fun onSpeed(speed: ReframeSpeed) = host.update { it.copy(reframeSpeed = speed) }

    fun onApply() {
        val ready = host.ready ?: return
        val clip = ready.project.clips.firstOrNull { it.id == ready.selectedClipId } ?: return
        if (!clip.canReframe(ready.project.frameAspect())) {
            host.send(EditorEvent.ReframeNothingToMove)
            return
        }
        run(listOf(clip), ready.reframeSpeed)
    }

    fun onApplyAll() {
        val ready = host.ready ?: return
        val aspect = ready.project.frameAspect()
        val clips = ready.project.clips.filter { it.canReframe(aspect) }
        if (clips.isEmpty()) {
            host.send(EditorEvent.ReframeNothingToMove)
            return
        }
        run(clips, ready.reframeSpeed)
    }

    /** Before/after: the preview shows the clip's whole picture, unmoved, until toggled back (not saved). */
    fun onShowOriginal() {
        host.update { it.copy(peekOriginal = !it.peekOriginal) }
        host.refreshPreview()
    }

    fun onCancel() {
        job?.cancel()
    }

    private fun run(clips: List<Clip>, speed: ReframeSpeed) {
        if (job?.isActive == true) return
        preview.pause()
        if (host.ready?.peekOriginal == true) host.update { it.copy(peekOriginal = false) }
        host.update { it.copy(reframing = ReframeProgress(clips.size, clips.first().timelineDurationMs(), 0, null)) }
        val started = SystemClock.elapsedRealtime()
        job = host.scope.launch {
            try {
                val edits = mutableListOf<EditAction>()
                var failed = 0
                clips.forEachIndexed { index, clip ->
                    val result = tracker.track(clip) { fraction -> progress((index + fraction) / clips.size, started) }
                    when (result) {
                        is TrackResult.Done -> edits += EditAction.AutoReframe(clip.id, result.samples, speed)
                        TrackResult.NoPerson -> Unit
                        is TrackResult.Failed -> failed++
                    }
                }
                when {
                    edits.isNotEmpty() -> {
                        host.applyEdit(edits.singleOrNull() ?: EditAction.Batch(edits))
                        host.send(if (clips.size == 1) EditorEvent.ReframeDone else EditorEvent.ReframedAll(edits.size))
                    }
                    failed == clips.size -> host.send(EditorEvent.ReframeFailed)
                    else -> host.send(if (clips.size == 1) EditorEvent.ReframeNoPerson else EditorEvent.ReframeNoneFound)
                }
            } finally {
                host.update { it.copy(reframing = null) }
            }
        }
    }

    private fun progress(fraction: Float, started: Long) {
        val percent = (fraction * 100).roundToInt().coerceIn(0, 99)
        val elapsed = SystemClock.elapsedRealtime() - started
        val left = if (fraction > ESTIMATE_AFTER) ((elapsed / fraction - elapsed) / 1_000).roundToInt() else null
        host.update { ready ->
            ready.reframing?.takeIf { it.percent != percent || it.secondsLeft != left }?.let { ready.copy(reframing = it.copy(percent = percent, secondsLeft = left)) } ?: ready
        }
    }

    private companion object {
        /** Estimates are guesses before this much progress. */
        const val ESTIMATE_AFTER = 0.05f
    }
}
