package com.savie.precut.model.shorts

import com.savie.precut.model.Project
import com.savie.precut.model.edit.SpokenWord
import com.savie.precut.model.edit.TimeRange
import com.savie.precut.model.edit.withoutTimeRanges
import com.savie.precut.model.totalDurationMs
import kotlin.math.roundToInt

/** A spoken line (a caption) on the timeline. */
data class Line(val startMs: Long, val endMs: Long, val text: String)

/**
 * What the shorts finder knows about a long video (Phase 26B): loudness per second of the timeline
 * (any scale), where the picture changes, and (with captions) the words and lines spoken.
 */
data class MomentSignals(
    val totalMs: Long,
    val energyPerSecond: List<Float>,
    val sceneCutsMs: List<Long>,
    val words: List<SpokenWord> = emptyList(),
    val lines: List<Line> = emptyList(),
)

/** A moment worth a short: [startMs] to [endMs] of the timeline, a title from its first line, and how strong its opening is (0–100). */
data class Moment(val startMs: Long, val endMs: Long, val title: String, val hookScore: Int) {
    val lengthMs: Long get() = endMs - startMs
}

const val MIN_SHORT_MS = 15_000L
const val MAX_SHORT_MS = 45_000L

/** A video needs to be at least this long for shorts to make sense. */
const val MIN_LONG_VIDEO_MS = 60_000L

private const val GRID_MS = 2_500L
private const val HOOK_WINDOW_MS = 3_000L

/**
 * The best [count] moments, not overlapping, strongest first. Moments start and end on line
 * boundaries when there are captions (so no sentence is cut), else on picture changes or a 2.5 s
 * grid. Each is scored on loudness (energy), how much is said, how strong its first 3 seconds are
 * (louder than the moment, a line ending in ? or !) and how often the picture changes.
 */
fun findMoments(signals: MomentSignals, count: Int = 5, minMs: Long = MIN_SHORT_MS, maxMs: Long = MAX_SHORT_MS): List<Moment> {
    if (signals.totalMs < minMs) return emptyList()
    val bounds = if (signals.lines.isNotEmpty()) {
        signals.lines.map { it.startMs } to signals.lines.map { it.endMs }
    } else {
        val grid = (0..signals.totalMs step GRID_MS).toList()
        val points = (grid + signals.sceneCutsMs).filter { it in 0..signals.totalMs }.distinct().sorted()
        points to points + signals.totalMs
    }
    val (starts, ends) = bounds
    val maxEnergy = signals.energyPerSecond.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val candidates = starts.mapNotNull { start ->
        ends.filter { it - start in minMs..maxMs }
            .map { end -> score(signals, start, end, maxEnergy) }
            .maxByOrNull { it.second }
    }.sortedByDescending { it.second }
    val chosen = mutableListOf<Moment>()
    for ((moment, _) in candidates) {
        if (chosen.size == count) break
        if (chosen.none { it.startMs < moment.endMs && moment.startMs < it.endMs }) chosen += moment
    }
    return chosen
}

private fun score(signals: MomentSignals, start: Long, end: Long, maxEnergy: Float): Pair<Moment, Float> {
    val energy = meanEnergy(signals, start, end) / maxEnergy
    val opening = meanEnergy(signals, start, minOf(end, start + HOOK_WINDOW_MS)) / maxEnergy
    val words = signals.words.count { it.startMs >= start && it.endMs <= end }
    val wordsPerSecond = words * 1_000f / (end - start)
    val speech = (wordsPerSecond / 3f).coerceAtMost(1f)
    val firstLine = signals.lines.firstOrNull { it.startMs >= start && it.startMs < end }
    val punch = if (firstLine != null && firstLine.startMs - start < HOOK_WINDOW_MS && firstLine.text.trim().lastOrNull() in setOf('?', '!')) 0.3f else 0f
    val hook = (opening / energy.coerceAtLeast(0.01f) * 0.5f + punch + if (firstLine?.startMs == start) 0.2f else 0f).coerceIn(0f, 1f)
    val cuts = signals.sceneCutsMs.count { it in start until end }
    val variety = (cuts * 10_000f / (end - start) / 2f).coerceAtMost(1f)
    val total = 0.4f * energy + 0.3f * speech + 0.2f * hook + 0.1f * variety
    val title = firstLine?.text?.trim()?.let { if (it.length > 40) it.take(39).trimEnd() + "…" else it }.orEmpty()
    return Moment(start, end, title, (hook * 100).roundToInt()) to total
}

private fun meanEnergy(signals: MomentSignals, start: Long, end: Long): Float {
    val from = (start / 1_000).toInt()
    val to = ((end + 999) / 1_000).toInt().coerceAtMost(signals.energyPerSecond.size)
    if (to <= from) return 0f
    return signals.energyPerSecond.subList(from, to).average().toFloat()
}

/**
 * [moment] of this project as its own upright short: everything outside it cut away (captions,
 * texts and sound stay lined up) and the frame set to 9:16. The caller gives it a new id and reframes it.
 */
fun Project.shortOf(moment: Moment): Project =
    withoutTimeRanges(listOf(TimeRange(0L, moment.startMs), TimeRange(moment.endMs, totalDurationMs())))

/** Picture changes this big (mean colour difference, 0..1) between samples a second apart count as a cut. */
const val SCENE_CUT_THRESHOLD = 0.12f

/**
 * Where the picture changes: [features] are coarse colour layouts of frames at [timesMs] (same
 * length each); a jump of at least [threshold] between neighbours is a cut at the later frame.
 */
fun sceneCuts(timesMs: List<Long>, features: List<FloatArray>, threshold: Float = SCENE_CUT_THRESHOLD): List<Long> =
    (1 until minOf(timesMs.size, features.size)).filter { i ->
        val a = features[i - 1]
        val b = features[i]
        a.indices.sumOf { kotlin.math.abs(a[it] - b[it]).toDouble() } / a.size >= threshold
    }.map { timesMs[it] }
