package com.savie.precut.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSerializationTest {

    @Test
    fun project_roundTripsThroughJson() {
        val original = Project(
            id = "p1",
            name = "Trip",
            aspectRatio = AspectRatio.RATIO_4_5,
            clips = listOf(
                videoClip("a", trimStartMs = 500L, trimEndMs = 4_000L, speed = 1.5f)
                    .copy(volume = 0f, filter = FilterType.VINTAGE, rotationDegrees = 270),
                clipFromMedia("b", "content://b", MediaInfo(ClipType.IMAGE, 0L, 800, 600, 0)),
            ),
            textOverlays = listOf(
                TextOverlay("t", "Hello", startMs = 1_000L, endMs = 3_000L, backgroundArgb = 0x80000000.toInt()),
            ),
            audioClips = listOf(
                AudioClip("m", "content://m", sourceDurationMs = 60_000L, timelineStartMs = 2_000L, trimEndMs = 30_000L),
            ),
            createdAt = 1L,
            updatedAt = 2L,
        )

        val json = Json.encodeToString(Project.serializer(), original)

        assertEquals(original, Json.decodeFromString(Project.serializer(), json))
    }
}
