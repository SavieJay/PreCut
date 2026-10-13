package com.savie.precut.media

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.ClipType
import com.savie.precut.model.DEFAULT_IMAGE_DURATION_MS
import com.savie.precut.model.OutputSize
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionSpecTest {

    @Test
    fun emptyProject_hasNoItems() {
        assertTrue(compositionSpecOf(project()).items.isEmpty())
    }

    @Test
    fun videoClip_mapsTrimToClipping_andKeepsSourceDuration() {
        val spec = compositionSpecOf(project(videoClip("a", 10_000L, trimStartMs = 1_500L, trimEndMs = 7_000L)))
        assertEquals(
            listOf(ItemSpec.Video("content://media/a", sourceDurationMs = 10_000L, clipStartMs = 1_500L, clipEndMs = 7_000L)),
            spec.items,
        )
    }

    @Test
    fun imageClip_usesTrimmedDisplayDuration() {
        val image = clipFromMedia("i", "content://i", MediaInfo(ClipType.IMAGE, 0L, 10, 10, 0))
            .copy(trimEndMs = 2_000L)
        assertEquals(listOf(ItemSpec.Image("content://i", durationMs = 2_000L)), compositionSpecOf(project(image)).items)
    }

    @Test
    fun allClips_areMappedInTimelineOrder_includingImages() {
        val image = clipFromMedia("i", "content://i", MediaInfo(ClipType.IMAGE, 0L, 4000, 3000, 0))
        val spec = compositionSpecOf(project(videoClip("a"), image, videoClip("b", trimStartMs = 2_000L)))
        assertEquals(
            listOf(
                ItemSpec.Video("content://media/a", 10_000L, 0L, 10_000L),
                ItemSpec.Image("content://i", DEFAULT_IMAGE_DURATION_MS),
                ItemSpec.Video("content://media/b", 10_000L, 2_000L, 10_000L),
            ),
            spec.items,
        )
    }

    @Test
    fun outputSize_followsProjectAspectRatio() {
        assertEquals(OutputSize(1080, 1920), compositionSpecOf(project()).outputSize) // default 9:16
        val landscape = project().copy(aspectRatio = AspectRatio.RATIO_16_9)
        assertEquals(OutputSize(1920, 1080), compositionSpecOf(landscape).outputSize)
    }
}
