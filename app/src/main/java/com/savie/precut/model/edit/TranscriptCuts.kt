package com.savie.precut.model.edit

import com.savie.precut.model.Project
import com.savie.precut.model.totalDurationMs

/** A spoken word on the timeline (from the captions), with the caption it belongs to. */
data class SpokenWord(val text: String, val startMs: Long, val endMs: Long, val captionId: String)

/** The captions' words in timeline order: the transcript Edit by transcript works on. */
fun Project.spokenWords(): List<SpokenWord> = textOverlays.filter { it.caption != null }.flatMap { caption ->
    caption.caption?.words.orEmpty().map { SpokenWord(it.text, caption.startMs + it.startMs, caption.startMs + it.endMs, caption.id) }
}.sortedBy { it.startMs }

/** Hesitations in English, the only language PreCut can tell fillers in so far. */
private val FILLERS = setOf("um", "umm", "uh", "uhh", "uhm", "erm", "er", "ah", "ahh", "hmm", "hm", "mm", "mhm")

/** The indexes of [words] that are fillers ("um", "uh"…, and "you know" as a pair). */
fun fillerIndices(words: List<SpokenWord>): Set<Int> {
    fun plain(i: Int) = words.getOrNull(i)?.text?.lowercase()?.trim { !it.isLetter() }.orEmpty()
    val found = mutableSetOf<Int>()
    words.indices.forEach { i ->
        if (plain(i) in FILLERS) found += i
        if (plain(i) == "you" && plain(i + 1) == "know") found += setOf(i, i + 1)
    }
    return found
}

/** Pauses longer than this between words are silences worth removing. */
const val MIN_SILENCE_MS = 800L

/** How much of a pause is kept on each side, so speech doesn't sound clipped. */
const val SILENCE_KEEP_MS = 150L

/**
 * The silences in [words]: the pauses of at least [minGapMs] between words, at the start and at the
 * end of a [totalMs] timeline, each less [SILENCE_KEEP_MS] at its edges next to speech.
 */
fun silenceRanges(words: List<SpokenWord>, totalMs: Long, minGapMs: Long = MIN_SILENCE_MS): List<TimeRange> {
    if (words.isEmpty()) return emptyList()
    val gaps = buildList {
        add(TimeRange(0L, (words.first().startMs - SILENCE_KEEP_MS).coerceAtLeast(0L)))
        words.zipWithNext { a, b -> add(TimeRange(a.endMs + SILENCE_KEEP_MS, b.startMs - SILENCE_KEEP_MS)) }
        add(TimeRange(words.last().endMs + SILENCE_KEEP_MS, totalMs))
    }
    return gaps.filter { it.lengthMs >= minGapMs - 2 * SILENCE_KEEP_MS && it.lengthMs > 0 }
}

/**
 * The timeline to take out for the words at [indices]: each word's own time, reaching into the
 * pauses around it (up to the neighbouring words) so no sliver of it is left.
 */
fun wordCuts(words: List<SpokenWord>, indices: Set<Int>): List<TimeRange> = indices.sorted().mapNotNull { i ->
    val word = words.getOrNull(i) ?: return@mapNotNull null
    val before = words.getOrNull(i - 1)?.endMs ?: (word.startMs - SILENCE_KEEP_MS)
    val after = words.getOrNull(i + 1)?.startMs ?: (word.endMs + SILENCE_KEEP_MS)
    TimeRange(maxOf(before, word.startMs - SILENCE_KEEP_MS).coerceAtLeast(0L), minOf(after, word.endMs + SILENCE_KEEP_MS))
}

/** Everything Edit by transcript takes out: the chosen words and silences, as one list for [EditAction.RemoveTimeRanges]. */
fun Project.transcriptCuts(words: List<SpokenWord>, wordIndices: Set<Int>, silences: List<TimeRange>): List<TimeRange> =
    normalizedRanges(wordCuts(words, wordIndices) + silences, totalDurationMs())
