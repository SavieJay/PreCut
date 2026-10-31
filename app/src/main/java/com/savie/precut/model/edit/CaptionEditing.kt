package com.savie.precut.model.edit

import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.captionsInOrder
import com.savie.precut.model.text.mergedWith
import com.savie.precut.model.text.splitCaption
import com.savie.precut.model.text.withCaptionText
import com.savie.precut.model.text.withCaptionTimes
import com.savie.precut.model.text.withHeardWords
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.withStyleOf

/** Applies a caption edit (Phase 21B); the same instance when nothing changed. */
internal fun Project.applyCaptionEdit(action: EditAction.CaptionEdit): Project = when (action) {
    is EditAction.SetCaptionText -> updateCaption(action.textId) { it.withCaptionText(action.text) }
    is EditAction.SetCaptionTimes -> setCaptionTimes(action)
    is EditAction.SplitCaption -> {
        val index = textOverlays.indexOfFirst { it.id == action.textId }
        val parts = textOverlays.getOrNull(index)?.splitCaption(action.atWord, action.newId, MIN_CLIP_DURATION_MS)
        if (parts == null) this else copy(textOverlays = textOverlays.subList(0, index) + parts.toList() + textOverlays.subList(index + 1, textOverlays.size))
    }
    is EditAction.MergeCaptionWithNext -> {
        val caption = textOverlays.firstOrNull { it.id == action.textId && it.caption != null }
        val next = caption?.let { captionAfter(it) }
        if (caption == null || next == null) {
            this
        } else {
            copy(textOverlays = textOverlays.mapNotNull { if (it.id == caption.id) caption.mergedWith(next) else it.takeIf { t -> t.id != next.id } })
        }
    }
    is EditAction.SetHeardWords ->
        if (action.words.isEmpty()) this else updateCaption(action.textId) { it.withHeardWords(action.words) }
    is EditAction.RestyleCaptions -> updateCaptions { action.style.applyTo(it) }
    is EditAction.StyleAllCaptions -> updateCaptions { caption ->
        if (caption.id == action.overlay.id) action.overlay else caption.withStyleOf(action.overlay)
    }
}

private fun Project.captionAfter(caption: TextOverlay): TextOverlay? =
    captionsInOrder().let { ordered -> ordered.getOrNull(ordered.indexOfFirst { it.id == caption.id } + 1) }

/** New times kept after the caption before, before the caption after, and at least [MIN_CLIP_DURATION_MS] long. */
private fun Project.setCaptionTimes(action: EditAction.SetCaptionTimes): Project {
    val ordered = captionsInOrder()
    val index = ordered.indexOfFirst { it.id == action.textId }
    if (index < 0) return this
    val earliest = ordered.getOrNull(index - 1)?.endMs ?: 0L
    val latest = ordered.getOrNull(index + 1)?.startMs ?: totalDurationMs()
    if (latest - earliest < MIN_CLIP_DURATION_MS) return this
    val start = action.startMs.coerceIn(earliest, latest - MIN_CLIP_DURATION_MS)
    val end = action.endMs.coerceIn(start + MIN_CLIP_DURATION_MS, latest)
    return updateCaption(action.textId) { it.withCaptionTimes(start, end) }
}

private fun Project.updateCaption(id: String, transform: (TextOverlay) -> TextOverlay): Project {
    val index = textOverlays.indexOfFirst { it.id == id && it.caption != null }
    if (index < 0) return this
    val edited = transform(textOverlays[index])
    if (edited == textOverlays[index]) return this
    return copy(textOverlays = textOverlays.toMutableList().also { it[index] = edited })
}

private fun Project.updateCaptions(transform: (TextOverlay) -> TextOverlay): Project {
    val edited = textOverlays.map { if (it.caption != null) transform(it) else it }
    return if (edited == textOverlays) this else copy(textOverlays = edited)
}
