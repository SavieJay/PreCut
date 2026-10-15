package com.savie.precut.ui.editor

import android.os.SystemClock
import com.savie.precut.media.MediaProbe
import com.savie.precut.media.PreviewController
import com.savie.precut.media.ProbeResult
import com.savie.precut.media.reverse.ClipReverser
import com.savie.precut.media.reverse.ReverseResult
import com.savie.precut.model.ClipType
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.frame.FrameGeometry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A reverse in progress (T-19): [secondsLeft] is null until there's enough progress to estimate. */
data class ReverseProgress(val clipLengthMs: Long, val percent: Int, val secondsLeft: Int?)

/**
 * Reverse (T-19): renders a reversed copy of the clip's range with progress and Cancel, then swaps
 * it in. Reversing a reversed clip restores the original at once.
 */
class EditorReverse(
    private val host: EditorHost,
    private val preview: PreviewController,
    private val reverser: ClipReverser,
    private val probe: MediaProbe,
) {
    private var job: Job? = null

    fun onReverse() {
        if (job?.isActive == true) return
        val clipId = host.targetClipId() ?: return
        val clip = host.ready?.project?.clips?.firstOrNull { it.id == clipId } ?: return
        if (clip.type != ClipType.VIDEO) {
            host.send(EditorEvent.ReverseNotForPhotos)
            return
        }
        if (clip.reversedFrom != null) {
            host.applyEdit(EditAction.UnreverseClip(clipId))
            host.send(EditorEvent.ClipUnreversed)
            return
        }
        preview.pause()
        val length = clip.trimEndMs - clip.trimStartMs
        val (width, height) = FrameGeometry.uprightSize(clip.sourceWidth, clip.sourceHeight, clip.sourceRotationDegrees, userRotation = 0)
        // At most 1080p (export's size today), never upscaled; even for the encoder.
        val shortSide = (minOf(width, height).takeIf { it > 0 } ?: MAX_SHORT_SIDE_PX).coerceAtMost(MAX_SHORT_SIDE_PX) / 2 * 2
        host.update { it.copy(reversing = ReverseProgress(length, 0, null)) }
        val started = SystemClock.elapsedRealtime()
        job = host.scope.launch {
            try {
                val result = reverser.reverse(clip.uri, clip.trimStartMs, clip.trimEndMs, shortSide) { fraction ->
                    val percent = (fraction * 100).roundToInt().coerceIn(0, 99)
                    val elapsed = SystemClock.elapsedRealtime() - started
                    val left = if (fraction > ESTIMATE_AFTER) ((elapsed / fraction - elapsed) / 1_000).roundToInt() else null
                    host.update { ready -> ready.reversing?.takeIf { it.percent != percent || it.secondsLeft != left }?.let { ready.copy(reversing = it.copy(percent = percent, secondsLeft = left)) } ?: ready }
                }
                when (result) {
                    is ReverseResult.Done -> {
                        val info = (probe.probe(result.uri) as? ProbeResult.Success)?.info
                        if (info == null) {
                            host.send(EditorEvent.ReverseFailed)
                        } else {
                            host.applyEdit(EditAction.ReverseClip(clipId, result.uri, info))
                            host.send(EditorEvent.ClipReversed)
                        }
                    }
                    ReverseResult.Unsupported -> host.send(EditorEvent.ReverseUnsupported)
                    is ReverseResult.Failed -> host.send(EditorEvent.ReverseFailed)
                }
            } finally {
                host.update { it.copy(reversing = null) }
            }
        }
    }

    fun onCancel() {
        job?.cancel()
    }

    private companion object {
        const val MAX_SHORT_SIDE_PX = 1080
        /** Estimates are guesses before this much progress. */
        const val ESTIMATE_AFTER = 0.05f
    }
}
