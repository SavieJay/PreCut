package com.savie.precut.model.ai.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.videoClip
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Three 10 s videos (c1–c3), a photo (c4), a text (t1) and music with beats (a1). */
fun editProject(): Project = Project(
    id = "p",
    name = "",
    clips = listOf(
        videoClip("v1"),
        videoClip("v2"),
        videoClip("v3"),
        Clip(id = "img", uri = "content://media/img", type = ClipType.IMAGE, sourceDurationMs = 60_000L, trimEndMs = 3_000L),
    ),
    textOverlays = listOf(TextOverlay(id = "title", text = "Hello", startMs = 0L, endMs = 2_000L)),
    audioClips = listOf(
        AudioClip(id = "song", uri = "content://media/song", sourceDurationMs = 60_000L, trimEndMs = 60_000L, displayName = "Song", beatsMs = (1..40).map { it * 1_500L }),
    ),
    createdAt = 0L,
    updatedAt = 0L,
)

/** Aliases as the brief gives them for [editProject]. */
fun editAliases(project: Project = editProject()): IdAliases = IdAliases().also { project.editBrief(it, 0L, emptySet()) }

fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

fun call(name: String, input: String, id: String = "toolu_$name") = EditToolCall(id, name, json(input))

/** Ids "n1", "n2", … */
fun counter(): () -> String {
    var next = 0
    return { "n${++next}" }
}
