package com.savie.precut.model.edit

import com.savie.precut.model.Clip
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipById
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.keyframe.ClipKeyframe
import com.savie.precut.model.keyframe.TextKeyframe
import com.savie.precut.model.keyframe.keyframeNear
import com.savie.precut.model.keyframe.valuesAt
import com.savie.precut.model.sourceToTimelineOffsetMs
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineOffsetToSourceMs

internal fun Project.applyKeyframeEdit(action: EditAction.KeyframeEdit): Project = when (action) {
    is EditAction.AddKeyframe -> keyframeClip(action.clipId, action.timelineMs) { clip, offset -> clip.withKeyframe(clip.snapshotAt(offset)) }
    is EditAction.RemoveKeyframe -> keyframeClip(action.clipId, action.timelineMs) { clip, offset ->
        clip.keyframeAt(offset)?.let { clip.copy(keyframes = clip.keyframes - it) } ?: clip
    }
    is EditAction.SetKeyframeEasing -> keyframeClip(action.clipId, action.timelineMs) { clip, offset ->
        clip.keyframeAt(offset)?.let { clip.withKeyframe(it.copy(easing = action.easing, curve = action.curve)) } ?: clip
    }
    is EditAction.EditKeyframed -> editKeyframed(action)
    is EditAction.AddTextKeyframe -> keyframeText(action.textId, action.timelineMs) { text, offset -> text.withKeyframe(text.snapshotAt(offset)) }
    is EditAction.RemoveTextKeyframe -> keyframeText(action.textId, action.timelineMs) { text, offset ->
        text.keyframeAt(offset)?.let { text.copy(keyframes = text.keyframes - it) } ?: text
    }
    is EditAction.SetTextKeyframeEasing -> keyframeText(action.textId, action.timelineMs) { text, offset ->
        text.keyframeAt(offset)?.let { text.withKeyframe(it.copy(easing = action.easing, curve = action.curve)) } ?: text
    }
    is EditAction.EditKeyframedText -> editKeyframedText(action)
}

/** The clip's keyframes with [ClipKeyframe.atMs] turned from source time into time into the clip (timeline ms). */
fun Clip.keyframesOnTimeline(): List<ClipKeyframe> =
    keyframes.map { it.copy(atMs = sourceToTimelineOffsetMs(it.atMs)) }.sortedBy { it.atMs }

/** Every animated value at [offsetMs] into the clip, as a keyframe there (source time); its plain values without keyframes. */
fun Clip.snapshotAt(offsetMs: Long): ClipKeyframe {
    val sourceMs = timelineOffsetToSourceMs(offsetMs)
    keyframesOnTimeline().valuesAt(offsetMs)?.let { return it.copy(atMs = sourceMs) }
    return ClipKeyframe(
        atMs = sourceMs,
        transform = transform,
        opacity = opacity,
        mask = mask,
        chromaIntensity = chroma?.intensity,
        chromaShadow = chroma?.shadow,
        filterIntensity = look.filterIntensity,
        adjust = look.adjust,
        volume = volume,
    )
}

/** The clip as it looks at [offsetMs] into it: keyframed values in place of the plain ones. For the editor's panels and gestures. */
fun Clip.atOffset(offsetMs: Long): Clip {
    if (keyframes.isEmpty()) return this
    val values = snapshotAt(offsetMs)
    return copy(
        transform = values.transform,
        opacity = values.opacity,
        mask = values.mask,
        chroma = chroma?.let { it.copy(intensity = values.chromaIntensity ?: it.intensity, shadow = values.chromaShadow ?: it.shadow) },
        look = look.copy(filterIntensity = values.filterIntensity, adjust = values.adjust),
        volume = values.volume,
    )
}

/** The keyframe at [offsetMs] into the clip (within a frame), if any. */
fun Clip.keyframeAt(offsetMs: Long): ClipKeyframe? = keyframes.keyframeNear(offsetMs) { sourceToTimelineOffsetMs(it.atMs) }

/** [keyframe] added, replacing any at the same moment, kept in time order. */
private fun Clip.withKeyframe(keyframe: ClipKeyframe): Clip {
    val offset = sourceToTimelineOffsetMs(keyframe.atMs)
    val others = keyframes.filterNot { it === keyframeAt(offset) }
    return copy(keyframes = (others + keyframe).sortedBy { it.atMs })
}

private fun Project.keyframeClip(clipId: String, timelineMs: Long, change: (Clip, Long) -> Clip): Project {
    val range = clipTimelineRange(clipId) ?: return this
    val clip = clipById(clipId) ?: return this
    val offset = (timelineMs - range.first).coerceIn(0L, clip.timelineDurationMs())
    return updateAnyClip(clipId) { change(it, offset) }
}

/** The edit applied as usual (clamping and all), then its animated value moved into the keyframe. */
private fun Project.editKeyframed(action: EditAction.EditKeyframed): Project {
    val clipId = when (val edit = action.edit) {
        is EditAction.SetClipTransform -> edit.clipId
        is EditAction.SetOpacity -> edit.clipId
        is EditAction.SetMask -> edit.clipId
        is EditAction.SetChroma -> edit.clipId
        is EditAction.SetClipLook -> edit.clipId
        is EditAction.SetClipVolume -> edit.clipId
        else -> return EditReducer.reduce(this, edit)
    }
    val plain = EditReducer.reduce(this, action.edit).clipById(clipId) ?: return this
    return keyframeClip(clipId, action.timelineMs) { clip, offset ->
        val base = clip.keyframeAt(offset) ?: clip.snapshotAt(offset)
        val keyframe = when (action.edit) {
            is EditAction.SetClipTransform -> base.copy(transform = plain.transform)
            is EditAction.SetOpacity -> base.copy(opacity = plain.opacity)
            is EditAction.SetMask -> base.copy(mask = plain.mask)
            is EditAction.SetChroma -> base.copy(chromaIntensity = plain.chroma?.intensity, chromaShadow = plain.chroma?.shadow)
            is EditAction.SetClipLook -> base.copy(filterIntensity = plain.look.filterIntensity, adjust = plain.look.adjust)
            else -> base.copy(volume = plain.volume)
        }
        // The plain values follow too, so they're sensible if every keyframe is removed later.
        plain.copy(keyframes = clip.keyframes).withKeyframe(keyframe)
    }
}

// Texts: keyframe times are time into the text.

/** The text's place, size, turn and opacity at [offsetMs] into it, as a keyframe there. */
fun TextOverlay.snapshotAt(offsetMs: Long): TextKeyframe =
    keyframes.valuesAt(offsetMs) ?: TextKeyframe(offsetMs, xNorm, yNorm, textSizeSp, rotationDegrees, opacity)

/** The text as it looks at [offsetMs] into it. */
fun TextOverlay.atOffset(offsetMs: Long): TextOverlay {
    if (keyframes.isEmpty()) return this
    val values = snapshotAt(offsetMs)
    return copy(xNorm = values.xNorm, yNorm = values.yNorm, textSizeSp = values.textSizeSp, rotationDegrees = values.rotationDegrees, opacity = values.opacity)
}

fun TextOverlay.keyframeAt(offsetMs: Long): TextKeyframe? = keyframes.keyframeNear(offsetMs, TextKeyframe::atMs)

private fun TextOverlay.withKeyframe(keyframe: TextKeyframe): TextOverlay {
    val others = keyframes.filterNot { it === keyframeAt(keyframe.atMs) }
    return copy(keyframes = (others + keyframe).sortedBy { it.atMs })
}

private fun Project.keyframeText(textId: String, timelineMs: Long, change: (TextOverlay, Long) -> TextOverlay): Project {
    val index = textOverlays.indexOfFirst { it.id == textId }
    if (index < 0) return this
    val text = textOverlays[index]
    val offset = (timelineMs - text.startMs).coerceIn(0L, text.endMs - text.startMs)
    val changed = change(text, offset)
    return if (changed == text) this else copy(textOverlays = textOverlays.toMutableList().also { it[index] = changed })
}

/**
 * Style and timing changes apply to the text; place, size, turn or opacity that differ from the
 * stored text go into the keyframe (the editor builds updates from the stored text).
 */
private fun Project.editKeyframedText(action: EditAction.EditKeyframedText): Project {
    val updated = action.overlay
    val old = textOverlays.firstOrNull { it.id == updated.id } ?: return this
    val withText = EditReducer.reduce(this, EditAction.UpdateText(updated.copy(keyframes = old.keyframes)))
    return withText.keyframeText(updated.id, action.timelineMs) { text, offset ->
        val base = text.keyframeAt(offset) ?: old.snapshotAt(offset)
        text.withKeyframe(
            base.copy(
                xNorm = if (updated.xNorm != old.xNorm) updated.xNorm else base.xNorm,
                yNorm = if (updated.yNorm != old.yNorm) updated.yNorm else base.yNorm,
                textSizeSp = if (updated.textSizeSp != old.textSizeSp) updated.textSizeSp else base.textSizeSp,
                rotationDegrees = if (updated.rotationDegrees != old.rotationDegrees) updated.rotationDegrees else base.rotationDegrees,
                opacity = if (updated.opacity != old.opacity) updated.opacity else base.opacity,
            ),
        )
    }
}

/** The clip [clipId] as shown at [timelineMs]: keyframed values in place (unchanged without keyframes). */
fun Project.clipShownAt(clipId: String?, timelineMs: Long): Clip? {
    val clip = clipById(clipId) ?: return null
    if (clip.keyframes.isEmpty()) return clip
    val range = clipTimelineRange(clip.id) ?: return clip
    return clip.atOffset((timelineMs - range.first).coerceIn(0L, clip.timelineDurationMs()))
}

/** The text [textId] as shown at [timelineMs]. */
fun Project.textShownAt(textId: String?, timelineMs: Long): TextOverlay? {
    val text = textOverlays.firstOrNull { it.id == textId } ?: return null
    return text.atOffset((timelineMs - text.startMs).coerceIn(0L, text.endMs - text.startMs))
}

/**
 * [action] as the editor should apply it at the playhead [timelineMs]: value edits on a clip or
 * text with keyframes become keyframe edits there; everything else is unchanged.
 */
fun Project.keyframed(action: EditAction, timelineMs: Long): EditAction {
    val clipId = when (action) {
        is EditAction.SetClipTransform -> action.clipId
        is EditAction.SetOpacity -> action.clipId
        is EditAction.SetMask -> action.clipId
        is EditAction.SetChroma -> action.clipId
        is EditAction.SetClipLook -> action.clipId
        is EditAction.SetClipVolume -> action.clipId
        is EditAction.UpdateText -> {
            val keyed = textOverlays.firstOrNull { it.id == action.overlay.id }?.keyframes?.isNotEmpty() == true
            return if (keyed) EditAction.EditKeyframedText(timelineMs, action.overlay) else action
        }
        else -> return action
    }
    return if (clipById(clipId)?.keyframes?.isNotEmpty() == true) EditAction.EditKeyframed(timelineMs, action) else action
}
