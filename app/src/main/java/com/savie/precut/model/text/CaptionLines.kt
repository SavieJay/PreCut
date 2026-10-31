package com.savie.precut.model.text

import com.savie.precut.model.AudioKind
import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.ai.TranscriptWord

/**
 * Automatic captions (Phase 21A): heard words grouped into short lines, then into caption texts.
 * A line breaks after a sentence, at a pause, or before it grows past [MAX_LINE_CHARS] or
 * [MAX_LINE_MS]; a comma ends a line that is already half full.
 */
object CaptionLines {
    const val MAX_LINE_CHARS = 32
    const val MAX_LINE_MS = 3_000L

    /** A silence this long between words starts a new line. */
    const val PAUSE_MS = 700L

    /** A line stays up this long after its last word, unless the next line starts sooner. */
    const val LINGER_MS = 400L

    private val SENTENCE_END = charArrayOf('.', '?', '!', '…', '。', '？', '！')
    private val CLAUSE_END = charArrayOf(',', ';', ':', '，', '、')

    /** [words] (timeline time) as lines, in order. Blank words are dropped. */
    fun build(
        words: List<TranscriptWord>,
        maxChars: Int = MAX_LINE_CHARS,
        maxLineMs: Long = MAX_LINE_MS,
        pauseMs: Long = PAUSE_MS,
    ): List<List<TranscriptWord>> {
        val lines = mutableListOf<List<TranscriptWord>>()
        var line = mutableListOf<TranscriptWord>()
        var chars = 0
        fun close() {
            if (line.isNotEmpty()) lines += line
            line = mutableListOf()
            chars = 0
        }
        words.asSequence()
            .map { it.copy(text = it.text.trim(), endMs = maxOf(it.endMs, it.startMs)) }
            .filter { it.text.isNotEmpty() }
            .sortedBy { it.startMs }
            .forEach { word ->
                val last = line.lastOrNull()
                if (last != null) {
                    val tooLong = chars + 1 + word.text.length > maxChars
                    val paused = word.startMs - last.endMs >= pauseMs
                    val tooSlow = word.endMs - line.first().startMs > maxLineMs
                    if (tooLong || paused || tooSlow) close()
                }
                chars += (if (line.isEmpty()) 0 else 1) + word.text.length
                line += word
                val end = word.text.last()
                if (end in SENTENCE_END || (end in CLAUSE_END && chars >= maxChars / 2)) close()
            }
        close()
        return lines
    }

    /**
     * One caption text per line, all in group [groupId] and styled by [style]. Word times are
     * stored into each caption, so moving it keeps them. Each text gets an id from [newId].
     */
    fun texts(
        lines: List<List<TranscriptWord>>,
        groupId: String,
        style: CaptionStyle,
        language: String?,
        newId: () -> String,
    ): List<TextOverlay> = lines.mapIndexedNotNull { index, words ->
        if (words.isEmpty()) return@mapIndexedNotNull null
        val start = words.first().startMs
        val lastEnd = words.maxOf { it.endMs }
        val nextStart = lines.getOrNull(index + 1)?.firstOrNull()?.startMs ?: Long.MAX_VALUE
        val end = maxOf(lastEnd, minOf(lastEnd + LINGER_MS, nextStart), start + 1)
        val caption = CaptionInfo(
            groupId = groupId,
            words = words.map { CaptionWord(it.text, it.startMs - start, it.endMs - start) },
            language = language,
        )
        style.applyTo(TextOverlay(id = newId(), text = words.joinToString(" ") { it.text }, startMs = start, endMs = end, caption = caption))
    }
}

/** What captions listen to: the project without its music, so lyrics don't become captions. */
fun Project.withoutMusic(): Project = copy(audioClips = audioClips.filter { it.kind != AudioKind.MUSIC })

/** Whether anything but music can be heard: a video's own sound, a voiceover or extracted sound. */
fun Project.hasSoundBesidesMusic(): Boolean =
    (!originalAudioMuted && clips.any { it.type == ClipType.VIDEO && !it.muted && it.volume > 0f }) ||
        overlays.any { it.clip.type == ClipType.VIDEO && !it.clip.muted && it.clip.volume > 0f } ||
        audioClips.any { it.kind != AudioKind.MUSIC && it.volume > 0f }

/** How many texts in the project are automatic captions. */
fun Project.captionCount(): Int = textOverlays.count { it.caption != null }

/** The project's captions in time order. */
fun Project.captionsInOrder(): List<TextOverlay> = textOverlays.filter { it.caption != null }.sortedBy { it.startMs }

/** The caption showing at timeline time [ms] (the later one where two overlap), or null. */
fun List<TextOverlay>.captionAt(ms: Long): TextOverlay? = lastOrNull { it.startMs <= ms && ms < it.endMs }

/** The preset every one of these captions was given, or null when they differ (or have none). */
fun List<TextOverlay>.sharedCaptionStyle(): CaptionStyle? = map { it.caption?.styleId }.distinct().singleOrNull()?.let(CaptionStyle::byId)
