package com.savie.precut.model.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.Clip
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineEndMs
import com.savie.precut.model.timelineOffsetToSourceMs
import com.savie.precut.model.totalDurationMs
import kotlin.math.roundToLong

/** A stretch of the timeline, [startMs] to [endMs] (exclusive). */
data class TimeRange(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

/** Shorter stretches aren't worth a cut (and would leave slivers). */
const val MIN_CUT_MS = 40L

/** [ranges] sorted, clamped to `0..totalMs`, with overlapping and touching ones merged and slivers dropped. */
fun normalizedRanges(ranges: List<TimeRange>, totalMs: Long): List<TimeRange> {
    val merged = mutableListOf<TimeRange>()
    ranges.map { TimeRange(it.startMs.coerceIn(0L, totalMs), it.endMs.coerceIn(0L, totalMs)) }
        .filter { it.lengthMs > 0 }
        .sortedBy { it.startMs }
        .forEach { range ->
            val last = merged.lastOrNull()
            if (last != null && range.startMs <= last.endMs) merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, range.endMs)) else merged += range
        }
    return merged.filter { it.lengthMs >= MIN_CUT_MS }
}

/** Where timeline time [t] lands once [ranges] (normalized) are taken out; inside one, at its start. */
fun mapThroughCuts(t: Long, ranges: List<TimeRange>): Long {
    var removed = 0L
    for (range in ranges) {
        if (t <= range.startMs) break
        removed += minOf(t, range.endMs) - range.startMs
    }
    return t - removed
}

/**
 * Edit by transcript (Phase 26B): takes [ranges] of the timeline out, closing the gaps. Main-track
 * clips (and voiceovers and extracted sound, which belong to the picture) are cut into the parts
 * that stay; music keeps playing, moved with what's before it; texts, captions (word by word),
 * overlays and effect and filter layers move with the time around them, and whatever lay wholly
 * inside a cut goes. Clips with a speed curve aren't cut (their timing can't be split reliably).
 * Unchanged when nothing would be left on the main track.
 */
fun Project.withoutTimeRanges(ranges: List<TimeRange>): Project {
    val cuts = normalizedRanges(ranges, totalDurationMs())
    if (cuts.isEmpty()) return this
    val starts = clipStartTimesMs()
    val keptClips = clips.flatMapIndexed { index, clip -> clip.keptParts(starts[index], cuts) }
    if (keptClips.isEmpty()) return this
    val map = { t: Long -> mapThroughCuts(t, cuts) }
    return copy(
        clips = keptClips,
        textOverlays = textOverlays.mapNotNull { it.throughCuts(cuts, map) },
        audioClips = audioClips.flatMap { it.throughCuts(cuts, map) },
        overlays = overlays.mapNotNull { overlay ->
            val start = overlay.timelineStartMs
            if (cuts.any { start >= it.startMs && overlay.timelineEndMs <= it.endMs }) null else overlay.copy(timelineStartMs = map(start))
        },
        effectLayers = effectLayers.mapNotNull { layer -> movedSpan(layer.startMs, layer.endMs, map)?.let { (s, e) -> layer.copy(startMs = s, endMs = e) } },
        lookLayers = lookLayers.mapNotNull { layer -> movedSpan(layer.startMs, layer.endMs, map)?.let { (s, e) -> layer.copy(startMs = s, endMs = e) } },
    )
}

/** The parts of a clip playing at [clipStart] that stay; one part keeps the clip's id, the others get derived ids. */
private fun Clip.keptParts(clipStart: Long, cuts: List<TimeRange>): List<Clip> {
    val length = timelineDurationMs()
    val inside = cuts.filter { it.endMs > clipStart && it.startMs < clipStart + length }
    if (inside.isEmpty() || speedCurve != null) return listOf(this)
    val spans = mutableListOf<Pair<Long, Long>>()
    var from = 0L
    inside.forEach { cut ->
        val cutStart = (cut.startMs - clipStart).coerceIn(0L, length)
        if (cutStart - from >= MIN_CLIP_DURATION_MS) spans += from to cutStart
        from = (cut.endMs - clipStart).coerceIn(0L, length)
    }
    if (length - from >= MIN_CLIP_DURATION_MS) spans += from to length
    return spans.mapIndexed { k, (a, b) ->
        val first = a == 0L
        val last = b == length
        copy(
            id = if (k == 0) id else "$id~$k",
            trimStartMs = timelineOffsetToSourceMs(a),
            trimEndMs = timelineOffsetToSourceMs(b),
            fadeInMs = if (first) fadeInMs else 0L,
            fadeOutMs = if (last) fadeOutMs else 0L,
            animationIn = if (first) animationIn else null,
            animationOut = if (last) animationOut else null,
            transitionOut = if (last) transitionOut else null,
        )
    }
}

private fun TextOverlay.throughCuts(cuts: List<TimeRange>, map: (Long) -> Long): TextOverlay? {
    val info = caption
    if (info == null) {
        val (start, end) = movedSpan(startMs, endMs, map) ?: return null
        return copy(startMs = start, endMs = end)
    }
    // Each spoken word goes with its own moment; words inside a cut go.
    val words = info.words.filter { word ->
        val at = startMs + word.startMs
        cuts.none { at >= it.startMs && startMs + word.endMs <= it.endMs }
    }
    if (info.words.isNotEmpty() && words.isEmpty()) return null
    val (start, end) = movedSpan(startMs, endMs, map) ?: return null
    val moved = words.map { word ->
        word.copy(startMs = (map(startMs + word.startMs) - start).coerceAtLeast(0L), endMs = (map(startMs + word.endMs) - start).coerceAtLeast(0L))
    }
    return copy(
        text = if (words.size == info.words.size) text else words.joinToString(" ") { it.text },
        startMs = start,
        endMs = end,
        caption = info.copy(words = moved),
        spans = if (words.size == info.words.size) spans else emptyList(),
    )
}

/** Music moves with what's before it; voiceovers and extracted sound are cut like the picture. */
private fun AudioClip.throughCuts(cuts: List<TimeRange>, map: (Long) -> Long): List<AudioClip> {
    if (kind == AudioKind.MUSIC) {
        return if (cuts.any { timelineStartMs >= it.startMs && timelineEndMs() <= it.endMs }) emptyList() else listOf(copy(timelineStartMs = map(timelineStartMs)))
    }
    val end = timelineEndMs()
    val inside = cuts.filter { it.endMs > timelineStartMs && it.startMs < end }
    if (inside.isEmpty()) return listOf(copy(timelineStartMs = map(timelineStartMs)))
    val spans = mutableListOf<Pair<Long, Long>>()
    var from = timelineStartMs
    inside.forEach { cut ->
        if (cut.startMs - from >= MIN_CLIP_DURATION_MS) spans += from to cut.startMs
        from = maxOf(from, cut.endMs)
    }
    if (end - from >= MIN_CLIP_DURATION_MS) spans += from to end
    fun source(t: Long) = trimStartMs + ((t - timelineStartMs) * speed.toDouble()).roundToLong()
    return spans.mapIndexed { k, (a, b) ->
        copy(
            id = if (k == 0) id else "$id~$k",
            timelineStartMs = map(a),
            trimStartMs = source(a),
            trimEndMs = source(b).coerceAtMost(trimEndMs),
            fadeInMs = if (a == timelineStartMs) fadeInMs else 0L,
            fadeOutMs = if (b == end) fadeOutMs else 0L,
            beatsMs = beatsMs.filter { it in source(a)..source(b) },
        )
    }
}

/** A span moved through the cuts; null when nothing of it is left. */
private fun movedSpan(start: Long, end: Long, map: (Long) -> Long): Pair<Long, Long>? {
    val s = map(start)
    val e = map(end)
    return if (e - s < MIN_CLIP_DURATION_MS) null else s to e
}
