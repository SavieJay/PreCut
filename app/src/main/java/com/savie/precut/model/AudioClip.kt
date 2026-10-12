package com.savie.precut.model

import kotlinx.serialization.Serializable

@Serializable
data class AudioClip(
    val id: String,
    val uri: String,
    val sourceDurationMs: Long,
    val timelineStartMs: Long = 0,
    val trimStartMs: Long = 0,
    val trimEndMs: Long,
    val volume: Float = 1f,
)
