package com.savie.precut.model.text

import com.savie.precut.model.TextOverlay
import com.savie.precut.model.withSpanStyle

/**
 * Word-by-word highlight (Phase 21A): while a caption shows, the word being spoken takes its
 * style's highlight colour. The renderer draws one picture per word, like the typewriter's frames.
 */

/** The word being spoken [localMs] into the caption: the last one started by then, or null before the first. */
fun CaptionInfo.wordAt(localMs: Long): Int? {
    val index = words.indexOfLast { it.startMs <= localMs }
    return index.takeIf { it >= 0 }
}

/**
 * Where each word is in [text], in order: its characters, or null when the words were edited and
 * it can't be found. Found words never overlap.
 */
fun CaptionInfo.wordRanges(text: String): List<IntRange?> {
    var from = 0
    return words.map { word ->
        val at = if (word.text.isEmpty()) -1 else text.indexOf(word.text, from)
        if (at < 0) {
            null
        } else {
            from = at + word.text.length
            at until from
        }
    }
}

/** The caption's word to highlight [localMs] into it; null when it has no highlight or no word is spoken yet. */
fun TextOverlay.highlightedWordAt(localMs: Long): Int? {
    val caption = caption ?: return null
    if (caption.highlightArgb == null) return null
    return caption.wordAt(localMs)
}

/** This caption with word [index] in its highlight colour; unchanged when it has none or the word isn't in the text. */
fun TextOverlay.withWordHighlighted(index: Int): TextOverlay {
    val colour = caption?.highlightArgb ?: return this
    val range = caption.wordRanges(text).getOrNull(index) ?: return this
    return withSpanStyle(range.first, range.last + 1, TextSpan(start = 0, end = 0, colorArgb = colour))
}
