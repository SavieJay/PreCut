package com.savie.precut.media.shorts

import android.content.Context
import android.util.Log
import com.savie.precut.media.audio.PcmDecoder
import com.savie.precut.media.cutout.FrameWalker
import com.savie.precut.media.cutout.SegmenterInput
import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.edit.spokenWords
import com.savie.precut.model.shorts.Line
import com.savie.precut.model.shorts.MomentSignals
import com.savie.precut.model.shorts.sceneCuts
import com.savie.precut.model.sourceToTimelineOffsetMs
import com.savie.precut.model.totalDurationMs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Long video to shorts (Phase 26B): what the moment finder needs from the media, read on the phone:
 * loudness for every second of the timeline, and where the picture changes (a coarse colour layout
 * of a frame a second); with the captions' words and lines. Nothing is uploaded.
 */
@Singleton
class MomentAnalyzer @Inject constructor(@ApplicationContext private val context: Context) {

    /** [project]'s signals; [onProgress] gets 0..1. Cancellable. A clip that can't be read adds silence. */
    suspend fun signals(project: Project, onProgress: (Float) -> Unit): MomentSignals = withContext(Dispatchers.Default) {
        val total = project.totalDurationMs()
        val seconds = ((total + 999) / 1_000).toInt()
        val sums = DoubleArray(seconds)
        val counts = IntArray(seconds)
        val cuts = mutableListOf<Long>()
        val starts = project.clipStartTimesMs()
        project.clips.forEachIndexed { index, clip ->
            if (clip.type != ClipType.VIDEO) return@forEachIndexed
            val start = starts[index]
            fun timeline(sourceUs: Long) = start + clip.sourceToTimelineOffsetMs(sourceUs / 1_000)
            val fromUs = clip.trimStartMs * 1_000
            val toUs = clip.trimEndMs * 1_000
            try {
                var channels = 1
                PcmDecoder.decode(context, clip.uri, onFormat = { _, c -> channels = c.coerceAtLeast(1) }, startUs = fromUs, endUs = toUs) { samples, count, timeUs ->
                    if (timeUs < fromUs || timeUs > toUs) return@decode
                    val second = (timeline(timeUs) / 1_000).toInt().coerceIn(0, seconds - 1)
                    var sum = 0.0
                    for (i in 0 until count) sum += samples[i].toDouble() * samples[i]
                    sums[second] += sum
                    counts[second] += count / channels
                }
                onProgress((index + 0.5f) / project.clips.size)
                val times = mutableListOf<Long>()
                val features = mutableListOf<FloatArray>()
                var next = fromUs
                FrameWalker.walk(context, clip.uri, fromUs, toUs, wanted = { it >= next }) { timeUs, rgb ->
                    next = timeUs + FRAME_STEP_US
                    times += timeline(timeUs)
                    features += layout(rgb)
                }
                cuts += sceneCuts(times, features)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't read a clip for shorts", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't decode a clip for shorts", e)
            }
            onProgress((index + 1f) / project.clips.size)
        }
        val energy = List(seconds) { i -> if (counts[i] == 0) 0f else sqrt(sums[i] / counts[i]).toFloat() / 32_768f }
        val lines = project.textOverlays.filter { it.caption != null }.sortedBy { it.startMs }.map { Line(it.startMs, it.endMs, it.text) }
        MomentSignals(total, energy, cuts.sorted(), project.spokenWords(), lines)
    }

    /** A 4 × 4 grid of mean colours of the segmenter's square RGB picture (0..1). */
    private fun layout(rgb: FloatArray): FloatArray {
        val side = SegmenterInput.SIDE
        val cell = side / GRID
        val out = FloatArray(GRID * GRID * 3)
        for (y in 0 until side) for (x in 0 until side) {
            val c = ((y / cell).coerceAtMost(GRID - 1) * GRID + (x / cell).coerceAtMost(GRID - 1)) * 3
            val p = (y * side + x) * 3
            out[c] += rgb[p]
            out[c + 1] += rgb[p + 1]
            out[c + 2] += rgb[p + 2]
        }
        val n = (cell * cell).toFloat()
        for (i in out.indices) out[i] /= n
        return out
    }

    private companion object {
        const val TAG = "MomentAnalyzer"
        const val GRID = 4
        const val FRAME_STEP_US = 1_000_000L
    }
}
