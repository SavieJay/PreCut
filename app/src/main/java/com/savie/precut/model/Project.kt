package com.savie.precut.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val clips: List<Clip> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    /** CapCut's "Mute clip audio": silences every video clip's own sound. */
    val originalAudioMuted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
