package com.savie.precut.model.text

import com.savie.precut.model.TextOverlay

/** Captions as a SubRip (.srt) subtitle file (Phase 21B). */
object Srt {

    /** The [texts] that are captions, in time order, numbered from 1; null when there are none. */
    fun of(texts: List<TextOverlay>): String? {
        val captions = texts.filter { it.caption != null && it.text.isNotBlank() }.sortedBy { it.startMs }
        if (captions.isEmpty()) return null
        return buildString {
            captions.forEachIndexed { index, caption ->
                append(index + 1).append('\n')
                append(time(caption.startMs)).append(" --> ").append(time(caption.endMs)).append('\n')
                // A blank line ends a cue, so the text can't contain one.
                append(caption.text.lines().filter { it.isNotBlank() }.joinToString("\n")).append("\n\n")
            }
        }
    }

    /** SubRip's time: 01:02:03,456. */
    fun time(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        val hours = t / 3_600_000
        val minutes = t / 60_000 % 60
        val seconds = t / 1_000 % 60
        return "%02d:%02d:%02d,%03d".format(java.util.Locale.ROOT, hours, minutes, seconds, t % 1_000)
    }

    /** A file name for [projectName]'s subtitles, safe on every file system. */
    fun fileName(projectName: String): String {
        val safe = projectName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
        return (safe.ifEmpty { "Captions" }) + ".srt"
    }
}
