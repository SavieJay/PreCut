package com.savie.precut.model

import com.savie.precut.model.look.LookLayer
import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val clips: List<Clip> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    /** Time-ranged filter/adjust layers over everything (CapCut's filter/adjust tracks). */
    val lookLayers: List<LookLayer> = emptyList(),
    /** Picture-in-picture clips above the main track (Phase 9). */
    val overlays: List<OverlayClip> = emptyList(),
    /** CapCut's "Mute clip audio": silences every video clip's own sound. */
    val originalAudioMuted: Boolean = false,
    /** Chosen cover; null means the first frame. */
    val cover: ProjectCover? = null,
    /** Shows the cover as the first frame of the video, so apps pick it as the thumbnail. */
    val coverInExport: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
