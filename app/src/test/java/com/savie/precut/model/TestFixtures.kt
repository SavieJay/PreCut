package com.savie.precut.model

fun videoClip(
    id: String = "v",
    sourceDurationMs: Long = 10_000L,
    trimStartMs: Long = 0L,
    trimEndMs: Long = sourceDurationMs,
    speed: Float = 1f,
) = Clip(
    id = id,
    uri = "content://media/$id",
    type = ClipType.VIDEO,
    sourceDurationMs = sourceDurationMs,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    speed = speed,
)

fun project(vararg clips: Clip) = Project(
    id = "p",
    name = "",
    clips = clips.toList(),
    createdAt = 0L,
    updatedAt = 0L,
)
