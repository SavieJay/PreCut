package com.savie.precut.media.ai

import android.content.Context
import android.util.Log
import com.savie.precut.media.broll.ImageClassifier
import com.savie.precut.media.export.FrameGrabber
import com.savie.precut.media.shorts.MomentAnalyzer
import com.savie.precut.model.Project
import com.savie.precut.model.ai.edit.MediaFacts
import com.savie.precut.model.ai.edit.Shot
import com.savie.precut.model.ai.edit.shotsFrom
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.totalDurationMs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Edit's media brief (Phase 27), made on the phone before anything is sent: loudness per second
 * and where the picture changes (the shorts analyzer), and what the image model sees in a frame of
 * each shot. Only these words and numbers are sent, never the media.
 */
@Singleton
class MediaBriefMaker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analyzer: MomentAnalyzer,
    private val frames: FrameGrabber,
) {
    private val labels: List<String> by lazy { context.assets.open(ImageClassifier.LABELS_ASSET).bufferedReader().readLines() }

    /** [project]'s facts; [onProgress] 0..1. Null when the media can't be read at all. */
    suspend fun facts(project: Project, onProgress: (Float) -> Unit): MediaFacts? = try {
        val signals = analyzer.signals(project) { onProgress(it * 0.7f) }
        val peak = signals.energyPerSecond.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val spans = shotsFrom(project.totalDurationMs(), signals.sceneCutsMs, project.clipStartTimesMs())
        val classifier = runCatching { ImageClassifier.create(context) }.getOrNull()
        val shots = try {
            spans.mapIndexed { index, (start, end) ->
                onProgress(0.7f + 0.3f * index / spans.size.coerceAtLeast(1))
                val seen = classifier?.let { model ->
                    frames.grab(project, (start + end) / 2, ImageClassifier.SIDE)?.let { frame ->
                        val scores = model.classify(frame)
                        frame.recycle()
                        scores.indices.sortedByDescending { scores[it] }.take(2).filter { scores[it] >= MIN_SEEN }.map { labels.getOrElse(it) { "" } }
                    }
                }.orEmpty()
                Shot(start, end, seen)
            }
        } finally {
            classifier?.close()
        }
        MediaFacts(signals.energyPerSecond.map { it / peak }, shots)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't read the media for the brief", e)
        null
    }

    private companion object {
        const val TAG = "MediaBrief"
        const val MIN_SEEN = 0.1f
    }
}
