package com.savie.precut.model.edit

import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.ReverseSource
import com.savie.precut.model.Project
import com.savie.precut.model.clipAt
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.motion.MAX_ANIMATION_MS
import com.savie.precut.model.motion.MAX_COMBO_SPEED
import com.savie.precut.model.motion.MAX_TRANSITION_MS
import com.savie.precut.model.motion.MIN_ANIMATION_MS
import com.savie.precut.model.motion.MIN_COMBO_SPEED
import com.savie.precut.model.motion.MIN_TRANSITION_MS

internal fun Project.applyMotionEdit(action: EditAction.MotionEdit): Project = when (action) {
    is EditAction.SetTransition -> updateMotion(action.clipId) { it.copy(transitionOut = action.transition?.clamped()) }
    is EditAction.SetAllTransitions -> {
        val transition = action.transition?.clamped()
        // Every junction: all clips but the last.
        val updated = clips.mapIndexed { index, clip -> if (index < clips.lastIndex) clip.copy(transitionOut = transition) else clip }
        if (updated == clips) this else copy(clips = updated)
    }
    is EditAction.SetAnimations -> updateMotion(action.clipId) {
        val combo = action.combo?.let { c -> c.copy(speed = c.speed.coerceIn(MIN_COMBO_SPEED, MAX_COMBO_SPEED)) }
        it.copy(
            animationIn = action.animationIn?.clamped()?.takeIf { combo == null },
            animationOut = action.animationOut?.clamped()?.takeIf { combo == null },
            animationCombo = combo,
        )
    }
    is EditAction.FreezeFrame -> freeze(action)
    is EditAction.ReverseClip -> updateMotion(action.clipId) { it.reversed(action.reversedUri, action.info) }
    is EditAction.UnreverseClip -> updateMotion(action.clipId) { it.unreversed() }
}

/** Plays [uri], the reversed copy of this clip's range; the original is kept to restore it. */
internal fun Clip.reversed(uri: String, info: MediaInfo): Clip {
    if (type != ClipType.VIDEO || reversedFrom != null || info.durationMs <= 0L) return this
    return copy(
        uri = uri,
        sourceDurationMs = info.durationMs,
        trimStartMs = 0L,
        trimEndMs = info.durationMs,
        sourceWidth = info.width,
        sourceHeight = info.height,
        sourceRotationDegrees = info.rotationDegrees,
        reversedFrom = ReverseSource(this.uri, sourceDurationMs, trimStartMs, trimEndMs, sourceWidth, sourceHeight, sourceRotationDegrees),
    )
}

/**
 * The original media again. A trim of the reversed copy ([trimStartMs]..[trimEndMs] of its
 * [sourceDurationMs]) is the mirror image of a range of the original.
 */
internal fun Clip.unreversed(): Clip {
    val from = reversedFrom ?: return this
    val length = sourceDurationMs
    val start = (from.trimStartMs + (length - trimEndMs)).coerceIn(0L, from.sourceDurationMs)
    val end = (from.trimStartMs + (length - trimStartMs)).coerceIn(start, from.sourceDurationMs)
    return copy(
        uri = from.uri,
        sourceDurationMs = from.sourceDurationMs,
        trimStartMs = start,
        trimEndMs = end,
        sourceWidth = from.sourceWidth,
        sourceHeight = from.sourceHeight,
        sourceRotationDegrees = from.sourceRotationDegrees,
        reversedFrom = null,
    )
}

/**
 * The two parts of a clip cut at source time [sourceMs]: the In animation stays on the first part,
 * the Out animation and the outgoing transition move to the second, a combo stays on both. A
 * speed curve is cut too, so each part keeps its share of the shape.
 */
internal fun Clip.splitAt(sourceMs: Long, secondId: String): Pair<Clip, Clip> {
    val at = (sourceMs - trimStartMs).toFloat() / (trimEndMs - trimStartMs).coerceAtLeast(1L)
    return copy(trimEndMs = sourceMs, transitionOut = null, animationOut = null, speedCurve = speedCurve?.slice(0f, at)) to
        copy(id = secondId, trimStartMs = sourceMs, animationIn = null, speedCurve = speedCurve?.slice(at, 1f))
}

private fun Project.freeze(action: EditAction.FreezeFrame): Project {
    val position = clipAt(action.atTimelineMs) ?: return this
    val clip = clips[position.index]
    if (clip.type != ClipType.VIDEO) return this
    val still = action.still.copy(
        look = clip.look,
        rotationDegrees = clip.rotationDegrees,
        mirrored = clip.mirrored,
        crop = clip.crop,
        cropRatio = clip.cropRatio,
        fit = clip.fit,
        transform = clip.transform,
        opacity = clip.opacity,
        canvas = clip.canvas,
    )
    val before = clips.subList(0, position.index)
    val after = clips.subList(position.index + 1, clips.size)
    val split = EditReducer.splitPoint(this, action.atTimelineMs)
    val middle = when {
        split != null -> clip.splitAt(split.second, action.secondPartId).let { (first, second) -> listOf(first, still, second) }
        // Too near an edge to split: before the clip in its first half, after it in its second.
        position.offsetMs * 2 < clip.timelineDurationMs() -> listOf(still, clip)
        // The still now sits between the clip and the next one, so it carries the transition on.
        else -> listOf(clip.copy(transitionOut = null), still.copy(transitionOut = clip.transitionOut))
    }
    return copy(clips = before + middle + after)
}

private fun ClipTransition.clamped() = copy(durationMs = durationMs.coerceIn(MIN_TRANSITION_MS, MAX_TRANSITION_MS))

private fun ClipAnimation.clamped() = copy(durationMs = durationMs.coerceIn(MIN_ANIMATION_MS, MAX_ANIMATION_MS))

private fun Project.updateMotion(clipId: String, transform: (Clip) -> Clip): Project = updateAnyClip(clipId, transform)
