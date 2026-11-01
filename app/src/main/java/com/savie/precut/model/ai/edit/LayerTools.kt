package com.savie.precut.model.ai.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.ClipType
import com.savie.precut.model.MAX_VOLUME
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.MIN_CLIP_DURATION_MS
import com.savie.precut.model.edit.beatCutPlan
import com.savie.precut.model.effect.EffectLayer
import com.savie.precut.model.effect.EffectSettings
import com.savie.precut.model.edit.TimeRange
import com.savie.precut.model.edit.normalizedRanges
import com.savie.precut.model.text.CaptionStyle
import com.savie.precut.model.text.TextTemplates
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.totalDurationMs

/** Texts, effects, the frame's shape and the audio clips. */

/** Where "top", "middle" and "bottom" texts sit (their centre, as a share of the frame's height). */
private val TEXT_ROWS = mapOf("top" to 0.15f, "middle" to 0.5f, "bottom" to 0.8f)

internal fun ToolContext.addText(): Made {
    val words = input.string("text").trim().ifEmpty { fail("text is empty") }
    val (start, end) = timelineRange("start_seconds", "end_seconds")
    val row = TEXT_ROWS[input.string("position")] ?: fail("position must be top, middle or bottom")
    val template = input.stringOrNull("template_id")?.let { id ->
        TextTemplates.all.firstOrNull { it.id == id } ?: fail("There's no text template $id; use one from catalog.text_templates")
    }
    val id = nextId()
    val plain = TextOverlay(id = id, text = words, startMs = start, endMs = end, yNorm = row)
    val overlay = template?.applyTo(plain) ?: plain
    return Made(EditAction.AddText(overlay), AiChangeLabel.AddText(words), newAlias = aliases.alias(id, AliasKind.TEXT))
}

internal fun ToolContext.editText() : Made {
    val overlay = text(input.string("text_id"))
    val words = input.stringOrNull("text")?.trim()?.also { if (it.isEmpty()) fail("text is empty; delete the text instead") }
    if (words != null && overlay.sticker != null) fail("That's a sticker: it has no words")
    val start = input.numberOrNull("start_seconds")?.let(::ms) ?: overlay.startMs
    val end = input.numberOrNull("end_seconds")?.let(::ms) ?: overlay.endMs
    checkRange(start, end)
    if (words == null && start == overlay.startMs && end == overlay.endMs) fail("Nothing to change: every value is null")
    val action = if (overlay.caption != null) {
        // A caption keeps its words' timings (Phase 21B).
        EditAction.Batch(
            listOfNotNull(
                words?.let { EditAction.SetCaptionText(overlay.id, it) },
                if (start != overlay.startMs || end != overlay.endMs) EditAction.SetCaptionTimes(overlay.id, start, end) else null,
            ),
        )
    } else {
        EditAction.UpdateText(overlay.copy(text = words ?: overlay.text, startMs = start, endMs = end))
    }
    return Made(action, AiChangeLabel.EditText(words ?: overlay.text))
}

internal fun ToolContext.deleteTexts(): Made {
    val named = input.strings("text_ids").ifEmpty { fail("text_ids is empty") }
    val ids = named.map { text(it).id }.distinct()
    return Made(EditAction.Batch(ids.map(EditAction::DeleteText)), AiChangeLabel.DeleteTexts(ids.size))
}

internal fun ToolContext.addEffect(): Made {
    val name = input.string("effect_id")
    val type = EditCatalog.effect(name) ?: fail("There's no effect $name; use one from catalog.effects")
    val (start, end) = timelineRange("start_seconds", "end_seconds")
    val intensity = inRange("intensity_percent", input.number("intensity_percent"), 0.0..100.0)
    val layer = EffectLayer(nextId(), start, end, EffectSettings(type, (intensity / 100).toFloat()))
    return Made(EditAction.AddEffectLayer(layer), AiChangeLabel.AddEffect(start, end))
}

internal fun ToolContext.aspectRatio(): Made {
    val name = input.string("ratio")
    val ratio = EditCatalog.ratio(name) ?: fail("There's no ratio $name")
    return Made(EditAction.SetAspectRatio(ratio), AiChangeLabel.Ratio(name), timelineChanged = false)
}

internal fun ToolContext.audioTrack(): Made {
    val audio = audio(input.string("audio_id"))
    val length = seconds(audio.timelineDurationMs())
    val volume = input.numberOrNull("volume_percent")?.let { inRange("volume_percent", it, 0.0..MAX_VOLUME * 100.0) }
    val fadeIn = input.numberOrNull("fade_in_seconds")?.let { ms(inRange("fade_in_seconds", it, 0.0..length)) }
    val fadeOut = input.numberOrNull("fade_out_seconds")?.let { ms(inRange("fade_out_seconds", it, 0.0..length)) }
    val start = input.numberOrNull("start_seconds")?.let { ms(inRange("start_seconds", it, 0.0..seconds(project.totalDurationMs()))) }
    val actions = listOfNotNull(
        volume?.let { EditAction.SetAudioVolume(audio.id, (it / 100).toFloat()) },
        if (fadeIn != null || fadeOut != null) EditAction.SetAudioFades(audio.id, fadeIn ?: audio.fadeInMs, fadeOut ?: audio.fadeOutMs) else null,
        start?.let { EditAction.SetAudioTiming(audio.id, it, audio.trimStartMs, audio.trimEndMs) },
    ).ifEmpty { fail("Nothing to change: every value is null") }
    return Made(EditAction.Batch(actions), AiChangeLabel.Music(audio.displayName))
}

internal fun ToolContext.cutToBeats(): Made {
    val audio = audio(input.string("audio_id"))
    if (audio.beatsMs.isEmpty()) fail("That audio has no beats marked")
    val plan = project.beatCutPlan(audio.id)
    if (plan.cut == 0) fail("No clip could be made to end on a beat (none starts before the music's last beat, or the clips are too short)")
    return Made(plan.edit, AiChangeLabel.CutToBeats(plan.cut), timelineChanged = true)
}

internal fun ToolContext.removeTimeRanges(): Made {
    val total = project.totalDurationMs()
    val ranges = input.ranges().map { (start, end) ->
        if (start < 0 || end <= start) fail("Each range needs end_seconds after start_seconds, both 0 or more")
        TimeRange(start, end)
    }
    val cuts = normalizedRanges(ranges, total).ifEmpty { fail("Nothing to cut: the ranges are outside the video or too short") }
    val removed = cuts.sumOf { it.lengthMs }
    if (total - removed < MIN_CLIP_DURATION_MS) fail("That would cut the whole video")
    return Made(EditAction.RemoveTimeRanges(cuts), AiChangeLabel.RemoveTime(removed), timelineChanged = true)
}

internal fun ToolContext.addCaptions(): Made {
    val style = input.string("style")
    if (CaptionStyle.entries.none { it.id == style }) fail("There's no caption style $style; use one from catalog.caption_styles")
    if (project.audioClips.none { it.kind != AudioKind.MUSIC } && project.clips.none { it.type == ClipType.VIDEO }) {
        fail("There's no speech to caption: no video or voiceover")
    }
    return Made(EditAction.Batch(emptyList()), AiChangeLabel.Captions(style))
}

private fun ToolContext.text(alias: String): TextOverlay {
    val real = aliases.real(alias) ?: fail("There's no text $alias")
    return project.textOverlays.firstOrNull { it.id == real } ?: fail("$alias isn't a text")
}

private fun ToolContext.audio(alias: String): AudioClip {
    val real = aliases.real(alias) ?: fail("There's no audio clip $alias")
    return project.audioClips.firstOrNull { it.id == real } ?: fail("$alias isn't an audio clip")
}

/** A start and end in timeline seconds, inside the video. */
private fun ToolContext.timelineRange(startName: String, endName: String): Pair<Long, Long> {
    val start = ms(input.number(startName))
    val end = ms(input.number(endName))
    checkRange(start, end)
    return start to end
}

private fun ToolContext.checkRange(start: Long, end: Long) {
    val total = project.totalDurationMs()
    if (start < 0 || end - start < MIN_CLIP_DURATION_MS) fail("The end must be at least 0.1 seconds after the start, both 0 or more")
    if (start >= total) fail("The video is only ${seconds(total)} seconds long")
}
