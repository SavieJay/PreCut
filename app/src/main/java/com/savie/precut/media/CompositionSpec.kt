package com.savie.precut.media

import com.savie.precut.model.ClipType
import com.savie.precut.model.Project

/**
 * Pure description of the Media3 composition for a project. Kept free of Media3/Android types so
 * the Project → composition mapping can be unit-tested on the JVM.
 */
data class CompositionSpec(val items: List<ItemSpec>)

sealed interface ItemSpec {
    data class Video(
        val uri: String,
        val sourceDurationMs: Long,
        val clipStartMs: Long,
        val clipEndMs: Long,
    ) : ItemSpec

    data class Image(val uri: String, val durationMs: Long) : ItemSpec
}

/** v1: the first clip only. Phase 3 maps every clip into the sequence. */
fun compositionSpecOf(project: Project): CompositionSpec {
    val clip = project.clips.firstOrNull() ?: return CompositionSpec(emptyList())
    val item = when (clip.type) {
        ClipType.VIDEO -> ItemSpec.Video(
            uri = clip.uri,
            sourceDurationMs = clip.sourceDurationMs,
            clipStartMs = clip.trimStartMs,
            clipEndMs = clip.trimEndMs,
        )
        ClipType.IMAGE -> ItemSpec.Image(uri = clip.uri, durationMs = clip.trimEndMs - clip.trimStartMs)
    }
    return CompositionSpec(listOf(item))
}
