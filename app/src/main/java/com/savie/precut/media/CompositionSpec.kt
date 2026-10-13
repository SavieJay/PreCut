package com.savie.precut.media

import com.savie.precut.model.ClipType
import com.savie.precut.model.OutputSize
import com.savie.precut.model.Project
import com.savie.precut.model.outputSizeFor

/**
 * Pure description of the Media3 composition for a project. Kept free of Media3/Android types so
 * the Project → composition mapping can be unit-tested on the JVM.
 */
data class CompositionSpec(val items: List<ItemSpec>, val outputSize: OutputSize)

sealed interface ItemSpec {
    data class Video(
        val uri: String,
        val sourceDurationMs: Long,
        val clipStartMs: Long,
        val clipEndMs: Long,
    ) : ItemSpec

    data class Image(val uri: String, val durationMs: Long) : ItemSpec
}

/** All clips in timeline order, each fitted into the project's output frame. */
fun compositionSpecOf(project: Project): CompositionSpec = CompositionSpec(
    items = project.clips.map { clip ->
        when (clip.type) {
            ClipType.VIDEO -> ItemSpec.Video(
                uri = clip.uri,
                sourceDurationMs = clip.sourceDurationMs,
                clipStartMs = clip.trimStartMs,
                clipEndMs = clip.trimEndMs,
            )
            ClipType.IMAGE -> ItemSpec.Image(uri = clip.uri, durationMs = clip.trimEndMs - clip.trimStartMs)
        }
    },
    outputSize = outputSizeFor(project.aspectRatio),
)
