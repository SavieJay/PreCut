package com.savie.precut.ui.editor

import android.os.SystemClock
import com.savie.precut.media.PreviewController
import com.savie.precut.media.audio.NoiseAnalysis
import com.savie.precut.media.audio.NoiseAnalyzer
import com.savie.precut.model.audio.NoiseReduction
import com.savie.precut.model.clipById
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.timelineDurationMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Reduce noise (Phase 22B) in the Volume panel of the selected audio clip, or else the selected
 * clip: switching it on measures the noise on the phone (progress and Cancel), then turns it on in
 * one undo step; Strength applies on release; off plays the original sound.
 */
class EditorNoise(
    private val host: EditorHost,
    private val preview: PreviewController,
    private val analyzer: NoiseAnalyzer,
) {
    private var job: Job? = null

    /** What the panel is about: the media, the range to measure, its length on the timeline, and how to set it. */
    private class Target(
        val uri: String,
        val range: LongRange,
        val lengthMs: Long,
        val current: NoiseReduction?,
        val set: (NoiseReduction?) -> EditAction,
    )

    fun onEnabled(enabled: Boolean) {
        val target = target() ?: return
        if (!enabled) {
            host.applyEdit(target.set(null))
        } else if (target.current == null) {
            measure(target)
        }
    }

    fun onStrength(strength: Float) {
        val target = target() ?: return
        val current = target.current ?: return
        host.applyEdit(target.set(current.copy(strength = strength)))
    }

    fun onCancel() {
        job?.cancel()
    }

    private fun measure(target: Target) {
        if (job?.isActive == true) return
        preview.pause()
        host.update { it.copy(measuringNoise = ReverseProgress(target.lengthMs, 0, null)) }
        val started = SystemClock.elapsedRealtime()
        job = host.scope.launch {
            try {
                val result = analyzer.analyze(target.uri, target.range.first, target.range.last) { fraction ->
                    val percent = (fraction * 100).roundToInt().coerceIn(0, 99)
                    val elapsed = SystemClock.elapsedRealtime() - started
                    val left = if (fraction > ESTIMATE_AFTER) ((elapsed / fraction - elapsed) / 1_000).roundToInt() else null
                    host.update { ready -> ready.measuringNoise?.takeIf { it.percent != percent || it.secondsLeft != left }?.let { ready.copy(measuringNoise = it.copy(percent = percent, secondsLeft = left)) } ?: ready }
                }
                when (result) {
                    is NoiseAnalysis.Done -> host.applyEdit(target.set(NoiseReduction(result.profile)))
                    NoiseAnalysis.NoSound -> host.send(EditorEvent.NoiseNoSound)
                    is NoiseAnalysis.Failed -> host.send(EditorEvent.NoiseFailed)
                }
            } finally {
                host.update { it.copy(measuringNoise = null) }
            }
        }
    }

    private fun target(): Target? {
        val ready = host.ready ?: return null
        ready.selectedAudioId?.let { id ->
            val audio = ready.project.audioClips.firstOrNull { it.id == id } ?: return null
            return Target(audio.uri, measured(audio.trimStartMs, audio.trimEndMs, audio.sourceDurationMs), audio.timelineDurationMs(), audio.noiseReduction) {
                EditAction.SetAudioNoiseReduction(id, it)
            }
        }
        val clip = ready.project.clipById(ready.selectedClipId) ?: return null
        return Target(clip.uri, measured(clip.trimStartMs, clip.trimEndMs, clip.sourceDurationMs), clip.timelineDurationMs(), clip.noiseReduction) {
            EditAction.SetClipNoiseReduction(clip.id, it)
        }
    }

    /** The part used, or the whole file when that's too short to hear the noise in (the noise is the same throughout). */
    private fun measured(startMs: Long, endMs: Long, sourceMs: Long): LongRange =
        if (endMs - startMs >= MIN_MEASURE_MS || sourceMs <= 0L) startMs..endMs else 0L..sourceMs

    private companion object {
        const val MIN_MEASURE_MS = 3_000L
        /** Estimates are guesses before this much progress. */
        const val ESTIMATE_AFTER = 0.05f
    }
}
