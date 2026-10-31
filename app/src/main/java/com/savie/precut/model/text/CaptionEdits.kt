package com.savie.precut.model.text

import com.savie.precut.model.TextOverlay
import com.savie.precut.model.ai.TranscriptWord

/**
 * Editing automatic captions (Phase 21B): words, times, cutting and joining lines, and words heard
 * again for one line. Word times stay "into the caption", as [CaptionInfo] keeps them.
 */

private val WHITESPACE = Regex("\\s+")

private fun wordsOf(text: String): List<String> = text.split(WHITESPACE).filter { it.isNotEmpty() }

/** [text]'s words spread across [durationMs] by length (a space's worth of pause after each); none for blank text. */
fun spreadWords(text: String, durationMs: Long): List<CaptionWord> {
    val words = wordsOf(text)
    if (words.isEmpty()) return emptyList()
    val total = words.sumOf { it.length + 1 }.toLong()
    var at = 0L
    return words.map { word ->
        val start = durationMs * at / total
        at += word.length + 1
        CaptionWord(word, start, maxOf(start, durationMs * (at - 1) / total))
    }
}

/**
 * This caption saying [newText]. Its word times stay when only the spacing changed; otherwise the
 * words are spread across the line, since there's no telling when edited words are spoken.
 */
fun TextOverlay.withCaptionText(newText: String): TextOverlay {
    val info = caption ?: return copy(text = newText)
    val unchanged = info.words.map { it.text } == wordsOf(newText)
    return copy(text = newText, caption = info.copy(words = if (unchanged) info.words else spreadWords(newText, endMs - startMs)))
}

/** This caption shown from [start] to [end]; its words stay where they're spoken, kept inside the line. */
fun TextOverlay.withCaptionTimes(start: Long, end: Long): TextOverlay {
    val info = caption ?: return copy(startMs = start, endMs = end)
    val shift = startMs - start
    val length = (end - start).coerceAtLeast(0L)
    val words = info.words.map { it.copy(startMs = (it.startMs + shift).coerceIn(0L, length), endMs = (it.endMs + shift).coerceIn(0L, length)) }
    return copy(startMs = start, endMs = end, caption = info.copy(words = words))
}

/**
 * The word Split cuts this caption before, at timeline time [atMs]: the first word spoken from
 * then on, kept so neither part is left without words. Null for a caption of fewer than two words.
 */
fun TextOverlay.captionSplitIndex(atMs: Long): Int? {
    val words = caption?.words ?: return null
    if (words.size < 2) return null
    val next = words.indexOfFirst { startMs + it.startMs >= atMs }.let { if (it < 0) words.size - 1 else it }
    return next.coerceIn(1, words.size - 1)
}

/**
 * This caption cut before word [index]: the first part keeps its id, the second gets [newId] and
 * starts where that word is spoken. Null when [index] would leave a part without words or shorter
 * than [minMs].
 */
fun TextOverlay.splitCaption(index: Int, newId: String, minMs: Long): Pair<TextOverlay, TextOverlay>? {
    val info = caption ?: return null
    if (index !in 1 until info.words.size) return null
    val cutMs = startMs + info.words[index].startMs
    if (cutMs - startMs < minMs || endMs - cutMs < minMs) return null
    val first = info.words.subList(0, index)
    val second = info.words.subList(index, info.words.size).map { it.copy(startMs = it.startMs - (cutMs - startMs), endMs = it.endMs - (cutMs - startMs)) }
    return copy(text = first.joinToString(" ") { it.text }, endMs = cutMs, caption = info.copy(words = first), spans = emptyList()) to
        copy(id = newId, text = second.joinToString(" ") { it.text }, startMs = cutMs, caption = info.copy(words = second), spans = emptyList())
}

/** This caption and [next] (the line after it) as one line, from this one's start to [next]'s end, in this one's style. */
fun TextOverlay.mergedWith(next: TextOverlay): TextOverlay {
    val info = caption ?: return this
    val shift = next.startMs - startMs
    val nextWords = next.caption?.words ?: spreadWords(next.text, next.endMs - next.startMs)
    val words = info.words + nextWords.map { it.copy(startMs = it.startMs + shift, endMs = it.endMs + shift) }
    return copy(
        text = listOf(text, next.text).filter { it.isNotBlank() }.joinToString(" "),
        endMs = maxOf(endMs, next.endMs),
        caption = info.copy(words = words),
        spans = emptyList(),
    )
}

/** The [heard] words (timeline time) spoken during this caption: those whose middle falls inside it. */
fun TextOverlay.wordsHeardIn(heard: List<TranscriptWord>): List<TranscriptWord> =
    heard.filter { (it.startMs + it.endMs) / 2 in startMs until endMs }.sortedBy { it.startMs }

/** This caption saying [words] (timeline time, heard again for this line); its times stay. */
fun TextOverlay.withHeardWords(words: List<TranscriptWord>): TextOverlay {
    val info = caption ?: return this
    val length = endMs - startMs
    val captionWords = words.map { CaptionWord(it.text.trim(), (it.startMs - startMs).coerceIn(0L, length), (it.endMs - startMs).coerceIn(0L, length)) }
        .filter { it.text.isNotEmpty() }
    return copy(text = captionWords.joinToString(" ") { it.text }, caption = info.copy(words = captionWords), spans = emptyList())
}
