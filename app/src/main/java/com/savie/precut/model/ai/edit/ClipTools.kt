package com.savie.precut.model.ai.edit

import com.savie.precut.model.ClipType
import com.savie.precut.model.MAX_CLIP_SPEED
import com.savie.precut.model.MAX_IMAGE_DURATION_MS
import com.savie.precut.model.MIN_CLIP_SPEED
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.edit.MIN_CLIP_DURATION_MS
import com.savie.precut.model.timelineDurationMs

/** Main-track tools: trim, split, delete, move, duplicate and speed. */

/** Slack for a keep range ending a hair past the source (seconds are rounded to 2 decimals in the brief). */
private const val SOURCE_SLACK_MS = 20L

internal fun ToolContext.trim(): Made {
    val (index, clip) = clip()
    val alias = input.string("clip_id")
    val from = ms(input.number("keep_from_seconds"))
    val to = ms(input.number("keep_to_seconds"))
    if (from < 0 || to - from < MIN_CLIP_DURATION_MS) fail("keep_to_seconds must be at least 0.1 seconds after keep_from_seconds, both 0 or more")
    val action = if (clip.type == ClipType.IMAGE) {
        // A photo has no source to keep part of: the kept length is how long it shows.
        if (to - from > MAX_IMAGE_DURATION_MS) fail("A photo shows for at most ${MAX_IMAGE_DURATION_MS / 1_000} seconds")
        EditAction.TrimClip(clip.id, 0L, to - from)
    } else {
        if (to > clip.sourceDurationMs + SOURCE_SLACK_MS) fail("$alias's source is only ${seconds(clip.sourceDurationMs)} seconds long")
        EditAction.TrimClip(clip.id, from, to.coerceAtMost(clip.sourceDurationMs))
    }
    val trimmed = EditReducer.reduce(project, action).clips[index]
    return Made(action, AiChangeLabel.Trim(index + 1, trimmed.timelineDurationMs()), timelineChanged = true)
}

internal fun ToolContext.split(): Made {
    val (index, clip) = clip()
    val at = ms(input.number("at_seconds"))
    if (at <= 0 || at >= clip.timelineDurationMs()) fail("at_seconds must be inside the clip: more than 0 and less than ${seconds(clip.timelineDurationMs())}")
    val timelineMs = project.clipStartTimesMs()[index] + at
    val point = EditReducer.splitPoint(project, timelineMs)
    if (point == null || point.first != index) fail("That's too close to the clip's start or end (or inside a transition) to split")
    val newId = nextId()
    return Made(
        EditAction.SplitClip(timelineMs, newId),
        AiChangeLabel.Split(index + 1, at),
        newAlias = aliases.alias(newId, AliasKind.CLIP),
        timelineChanged = true,
    )
}

internal fun ToolContext.deleteClips(): Made {
    val named = input.strings("clip_ids").ifEmpty { fail("clip_ids is empty") }
    val ids = named.map { alias ->
        val real = aliases.real(alias) ?: fail("There's no clip $alias")
        project.clips.firstOrNull { it.id == real }?.id ?: fail("$alias isn't a clip on the main track")
    }.toSet()
    if (ids.size >= project.clips.size) fail("At least one clip must stay on the main track")
    return Made(EditAction.RemoveItems(ids, emptySet()), AiChangeLabel.DeleteClips(ids.size), timelineChanged = true)
}

internal fun ToolContext.move(): Made {
    val (index, _) = clip()
    val position = input.number("to_position")
    val to = position.toInt()
    if (to.toDouble() != position || to !in project.clips.indices) fail("to_position must be a whole number from 0 to ${project.clips.lastIndex}")
    if (to == index) fail("That clip is already at position $to")
    return Made(EditAction.MoveClip(index, to), AiChangeLabel.Move(index + 1, to + 1), timelineChanged = true)
}

internal fun ToolContext.duplicate(): Made {
    val (index, clip) = clip()
    val newId = nextId()
    return Made(
        EditAction.DuplicateClip(clip.id, newId),
        AiChangeLabel.Duplicate(index + 1),
        newAlias = aliases.alias(newId, AliasKind.CLIP),
        timelineChanged = true,
    )
}

internal fun ToolContext.speed(): Made {
    val (index, clip) = clip()
    if (clip.type == ClipType.IMAGE) fail("Photos have no speed; trim a photo to change how long it shows")
    val speed = inRange("speed", input.number("speed"), MIN_CLIP_SPEED.toDouble()..MAX_CLIP_SPEED.toDouble()).toFloat()
    return Made(EditAction.SetSpeed(clip.id, speed), AiChangeLabel.Speed(index + 1, speed), timelineChanged = true)
}
