package com.savie.precut.media

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.FilterType
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.ClipType
import com.savie.precut.model.DEFAULT_IMAGE_DURATION_MS
import com.savie.precut.model.OutputSize
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun speedFilterAndRotation_areCarriedPerItem() {
        val clip = videoClip("a").copy(speed = 2f, filter = FilterType.GRAYSCALE, rotationDegrees = 90)
        val item = compositionSpecOf(project(clip)).items.single() as ItemSpec.Video
        assertEquals(2f, item.speed)
        assertEquals(VisualSpec(listOf(FilterEffect.Grayscale), rotationDegrees = 90), item.visual)
    }

    @Test
    fun filters_mapToEffects() {
        assertEquals(emptyList<FilterEffect>(), filterEffectsFor(FilterType.NONE))
        FilterType.entries.filter { it != FilterType.NONE }.forEach { assertTrue(it.name, filterEffectsFor(it).isNotEmpty()) }
    }

    @Test
    fun vintageMatrix_isColumnMajorBlendOfSepia() {
        // Output red from input red: 0.8 * 0.393 + 0.2 = 0.5144; output red from input green: 0.8 * 0.769.
        assertEquals(0.5144f, VINTAGE_MATRIX[0], 1e-4f)
        assertEquals(0.8f * 0.769f, VINTAGE_MATRIX[4], 1e-4f)
        assertEquals(1f, VINTAGE_MATRIX[15])
        assertEquals(0f, VINTAGE_MATRIX[12])
    }

    @Test
    fun texts_mapToMicrosecondsNdcAndOutputPixels() {
        val overlay = TextOverlay("t", "Hello", startMs = 1_000L, endMs = 2_500L, xNorm = 0.25f, yNorm = 0.75f, textSizeSp = 36f)
        val spec = compositionSpecOf(project(videoClip("a")).copy(textOverlays = listOf(overlay))).texts.single()
        assertEquals(1_000_000L to 2_500_000L, spec.startUs to spec.endUs)
        assertEquals(-0.5f to -0.5f, spec.anchorX to spec.anchorY)
        assertEquals(108f, spec.textSizePx) // 36 sp * 1080 / 360
    }

    @Test
    fun blankTexts_areNotRendered() {
        val blank = TextOverlay("t", "  ", startMs = 0L, endMs = 1_000L)
        assertTrue(compositionSpecOf(project(videoClip("a")).copy(textOverlays = listOf(blank))).texts.isEmpty())
    }

    @Test
    fun textVisibility_isStartInclusiveEndExclusive() {
        val spec = TextSpec("t", "x", 1_000L, 2_000L, 0f, 0f, 10f, 0, null)
        assertFalse(spec.isVisibleAt(999L))
        assertTrue(spec.isVisibleAt(1_000L))
        assertTrue(spec.isVisibleAt(1_999L))
        assertFalse(spec.isVisibleAt(2_000L))
    }

    @Test
    fun ndc_flipsYAndCentres() {
        assertEquals(0f to 0f, toNdc(0.5f, 0.5f))
        assertEquals(-1f to 1f, toNdc(0f, 0f)) // top-left
        assertEquals(1f to -1f, toNdc(1f, 1f)) // bottom-right
    }
}
