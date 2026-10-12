package com.savie.precut.media

import com.savie.precut.model.ClipType
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
    fun v1_mapsFirstClipOnly() {
        val spec = compositionSpecOf(project(videoClip("a"), videoClip("b")))
        assertEquals(1, spec.items.size)
        assertEquals("content://media/a", (spec.items.single() as ItemSpec.Video).uri)
    }
}
