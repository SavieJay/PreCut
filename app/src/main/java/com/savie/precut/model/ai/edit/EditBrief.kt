package com.savie.precut.model.ai.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.timelineEndMs
import com.savie.precut.model.totalDurationMs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

/**
 * What Claude is told about a project (Phase 22C): the timeline's items by alias, their times in
 * seconds, and the catalogs to pick looks from. Never media, file names of clips, or anything
 * outside the project; texts' words and music names are included, as the consent says.
 */
fun Project.editBrief(aliases: IdAliases, playheadMs: Long, selectedIds: Set<String>): JsonObject = buildJsonObject {
    put("duration_seconds", seconds(totalDurationMs()))
    put("aspect_ratio", EditCatalog.ratioName(aspectRatio))
    put("playhead_seconds", seconds(playheadMs))
    if (originalAudioMuted) put("all_clip_sound_muted", true)
    put("clips", JsonArray(clips.zip(clipStartTimesMs()) { clip, start -> clipBrief(clip, start, aliases) }))
    if (overlays.isNotEmpty()) {
        put("overlays", buildJsonArray {
            overlays.forEach { overlay ->
                add(buildJsonObject {
                    put("id", aliases.alias(overlay.id, AliasKind.OVERLAY))
                    put("type", overlay.clip.type.catalogId)
                    put("start_seconds", seconds(overlay.timelineStartMs))
                    put("length_seconds", seconds(overlay.clip.timelineDurationMs()))
                })
            }
        })
    }
    put("texts", JsonArray(textOverlays.sortedBy { it.startMs }.map { textBrief(it, aliases) }))
    put("audio", JsonArray(audioClips.sortedBy { it.timelineStartMs }.map { audioBrief(it, aliases) }))
    // After the lists, so aliases number the items in timeline order.
    put("selected", JsonArray(selectedIds.mapNotNull { aliasOf(aliases, it) }.map(::JsonPrimitive)))
    if (effectLayers.isNotEmpty()) {
        put("effects", buildJsonArray {
            effectLayers.forEach { layer ->
                add(buildJsonObject {
                    put("effect", layer.effect.type.catalogId)
                    put("start_seconds", seconds(layer.startMs))
                    put("end_seconds", seconds(layer.endMs))
                })
            }
        })
    }
    put("catalog", buildJsonObject {
        put("filters", strings(EditCatalog.filters))
        put("effects", strings(EditCatalog.effects))
        put("transitions", strings(EditCatalog.transitions))
        put("animations", strings(EditCatalog.animations))
        put("combos", strings(EditCatalog.combos))
        put("text_templates", strings(EditCatalog.textTemplates))
    })
}

/** The main track's timeline after a change, for a tool's result. */
fun Project.timelineBrief(aliases: IdAliases): JsonObject = buildJsonObject {
    put("duration_seconds", seconds(totalDurationMs()))
    put("clips", buildJsonArray {
        clips.zip(clipStartTimesMs()) { clip, start ->
            add(buildJsonObject {
                put("id", aliases.alias(clip.id, AliasKind.CLIP))
                put("start_seconds", seconds(start))
                put("length_seconds", seconds(clip.timelineDurationMs()))
            })
        }
    })
}

private fun clipBrief(clip: Clip, start: Long, aliases: IdAliases): JsonObject = buildJsonObject {
    put("id", aliases.alias(clip.id, AliasKind.CLIP))
    put("type", clip.type.catalogId)
    put("start_seconds", seconds(start))
    put("length_seconds", seconds(clip.timelineDurationMs()))
    if (clip.type == ClipType.VIDEO) {
        put("source_seconds", seconds(clip.sourceDurationMs))
        put("keep_from_seconds", seconds(clip.trimStartMs))
        put("keep_to_seconds", seconds(clip.trimEndMs))
        if (clip.speedCurve != null) put("speed", "curve") else if (clip.speed != 1f) put("speed", clip.speed)
        if (clip.muted) put("muted", true) else if (clip.volume != 1f) put("volume_percent", percent(clip.volume))
    }
    clip.look.filterId?.let { put("filter", it) }
    clip.transitionOut?.let { put("transition_out", "${it.type.catalogId} ${seconds(it.durationMs)}s") }
    clip.animationIn?.let { put("in_animation", it.type.catalogId) }
    clip.animationOut?.let { put("out_animation", it.type.catalogId) }
    clip.animationCombo?.let { put("combo", it.type.catalogId) }
    if (clip.keyframes.isNotEmpty()) put("has_keyframes", true)
}

private fun textBrief(text: TextOverlay, aliases: IdAliases): JsonObject = buildJsonObject {
    put("id", aliases.alias(text.id, AliasKind.TEXT))
    when {
        text.sticker != null -> put("sticker", true)
        else -> put("text", text.text)
    }
    if (text.caption != null) put("caption", true)
    putTimes(text.startMs, text.endMs)
}

private fun audioBrief(audio: AudioClip, aliases: IdAliases): JsonObject = buildJsonObject {
    put("id", aliases.alias(audio.id, AliasKind.AUDIO))
    put("kind", audio.kind.catalogId)
    if (audio.displayName.isNotBlank()) put("name", audio.displayName)
    putTimes(audio.timelineStartMs, audio.timelineEndMs())
    if (audio.volume != 1f) put("volume_percent", percent(audio.volume))
    put("beats", audio.beatsMs.size)
}

private fun JsonObjectBuilder.putTimes(startMs: Long, endMs: Long) {
    put("start_seconds", seconds(startMs))
    put("end_seconds", seconds(endMs))
}

private fun Project.aliasOf(aliases: IdAliases, id: String): String? = when {
    clips.any { it.id == id } -> aliases.alias(id, AliasKind.CLIP)
    overlays.any { it.id == id } -> aliases.alias(id, AliasKind.OVERLAY)
    textOverlays.any { it.id == id } -> aliases.alias(id, AliasKind.TEXT)
    audioClips.any { it.id == id } -> aliases.alias(id, AliasKind.AUDIO)
    else -> null
}

private fun strings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

/** Milliseconds as seconds with two decimals. */
internal fun seconds(ms: Long): Double = (ms / 10.0).roundToLong() / 100.0

internal fun percent(gain: Float): Int = (gain * 100).toDouble().roundToLong().toInt()
