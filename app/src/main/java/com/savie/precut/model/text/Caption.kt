package com.savie.precut.model.text

import kotlinx.serialization.Serializable

/**
 * Automatic captions (Phase 21). A caption is an ordinary [com.savie.precut.model.TextOverlay]
 * with [com.savie.precut.model.TextOverlay.caption] set; everything else (style, place, edits,
 * undo) works as for any text.
 */

/** One spoken word. Times are into the caption (from its `startMs`), so moving the caption keeps them. */
@Serializable
data class CaptionWord(val text: String, val startMs: Long, val endMs: Long)

@Serializable
data class CaptionInfo(
    /** Captions made by one Generate share a group; generating again replaces the whole group. */
    val groupId: String,
    /** The words of this line in order, with their times; the line's text is these words joined. */
    val words: List<CaptionWord> = emptyList(),
    /** The style preset the line was given, if any (a `CaptionStyle` id). */
    val styleId: String? = null,
    /** Colour for the word being spoken; null draws no word highlight. */
    val highlightArgb: Int? = null,
    /** The language heard, as the provider gave it (e.g. "eng"). */
    val language: String? = null,
)
