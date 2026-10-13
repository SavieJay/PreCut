package com.savie.precut.media

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.AudioClip
import com.savie.precut.model.FilterType
import com.savie.precut.model.VoiceEffect
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.ClipType
import com.savie.precut.model.DEFAULT_IMAGE_DURATION_MS
import com.savie.precut.model.OutputSize
import com.savie.precut.model.CoverSource
import com.savie.precut.model.ProjectCover
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
            listOf(
                ItemSpec.Video(
                    "content://media/a", sourceDurationMs = 10_000L, clipStartMs = 1_500L, clipEndMs = 7_000L,
                    sound = SoundSpec(lengthMs = 5_500L),
                ),
            ),
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
                ItemSpec.Video("content://media/a", 10_000L, 0L, 10_000L, sound = SoundSpec(lengthMs = 10_000L)),
                ItemSpec.Image("content://i", DEFAULT_IMAGE_DURATION_MS),
                ItemSpec.Video("content://media/b", 10_000L, 2_000L, 10_000L, sound = SoundSpec(lengthMs = 8_000L)),
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

    @Test
    fun clipSound_isSilencedByClipOrOriginalMute_andCarriesFades() {
        val loud = videoClip("a").copy(volume = 1.4f, fadeInMs = 500L, fadeOutMs = 800L)
        val items = compositionSpecOf(project(loud, videoClip("b").copy(muted = true))).items.map { (it as ItemSpec.Video).sound }
        assertEquals(SoundSpec(1.4f, 500L, 800L, 10_000L), items[0])
        assertEquals(0f, items[1].volume)
        val allMuted = compositionSpecOf(project(loud).copy(originalAudioMuted = true)).items.single() as ItemSpec.Video
        assertEquals(0f, allMuted.sound.volume)
    }

    @Test
    fun audioLanes_mapGapsClippingSpeedAndSound_cutAtVideoEnd() {
        // 10 s of video. Row 1: a (0-4 s) then c (6 s, 2x, cut at 10 s). Row 2: b overlaps a.
        val a = AudioClip("a", "content://a", 30_000L, timelineStartMs = 0L, trimEndMs = 4_000L, volume = 0.5f)
        val b = AudioClip("b", "content://b", 30_000L, timelineStartMs = 1_000L, trimEndMs = 2_000L, voiceEffect = VoiceEffect.DEEP)
        val c = AudioClip("c", "content://c", 30_000L, timelineStartMs = 6_000L, trimStartMs = 1_000L, trimEndMs = 30_000L, speed = 2f, fadeOutMs = 1_000L)
        val lanes = compositionSpecOf(project(videoClip("v")).copy(audioClips = listOf(c, b, a))).audioLanes
        assertEquals(2, lanes.size)
        assertEquals(
            listOf(
                AudioItemSpec("content://a", 30_000L, gapBeforeMs = 0L, clipStartMs = 0L, clipEndMs = 4_000L, speed = 1f, sound = SoundSpec(0.5f, 0L, 0L, 4_000L)),
                AudioItemSpec("content://c", 30_000L, gapBeforeMs = 2_000L, clipStartMs = 1_000L, clipEndMs = 9_000L, speed = 2f, sound = SoundSpec(1f, 0L, 1_000L, 4_000L)),
            ),
            lanes[0],
        )
        assertEquals(1_000L, lanes[1].single().gapBeforeMs)
        assertEquals(VoiceEffect.DEEP.pitch, lanes[1].single().sound.pitch)
    }

    @Test
    fun audioPastTheVideoEnd_isOmitted_andEmptyLanesDropped() {
        val late = AudioClip("m", "content://m", 30_000L, timelineStartMs = 12_000L, trimEndMs = 30_000L)
        assertTrue(compositionSpecOf(project(videoClip("a")).copy(audioClips = listOf(late))).audioLanes.isEmpty())
    }

    // Phase 7

    @Test
    fun keepPitch_isPassedThrough() {
        val clip = videoClip("a", speed = 2f).copy(keepPitch = false)
        assertEquals(false, (compositionSpecOf(project(clip)).items.single() as ItemSpec.Video).keepPitch)
    }

    @Test
    fun textRotationAndOpacity_areMapped() {
        val text = TextOverlay("t", "Hi", 0L, 1_000L, rotationDegrees = 45f, opacity = 0.5f)
        val spec = compositionSpecOf(project(videoClip("a")).copy(textOverlays = listOf(text))).texts.single()
        assertEquals(45f, spec.rotationDegrees)
        assertEquals(0.5f, spec.opacity)
    }

    @Test
    fun cover_isInSpecOnlyWhenChosenAndAddedToExport() {
        val base = project(videoClip("a"), videoClip("b"))
        assertEquals(null, compositionSpecOf(base).cover)
        assertEquals(null, compositionSpecOf(base.copy(cover = ProjectCover.Frame(12_000L))).cover)
        val spec = compositionSpecOf(base.copy(cover = ProjectCover.Frame(12_000L), coverInExport = true)).cover
        assertEquals(CoverSpec(CoverSource("content://media/b", ClipType.VIDEO, 2_000L, 0), COVER_DURATION_US), spec)
    }

    @Test
    fun originalRatio_followsFirstClip() {
        val clip = videoClip("a").copy(sourceWidth = 1920, sourceHeight = 1080)
        assertEquals(OutputSize(1920, 1080), compositionSpecOf(project(clip).copy(aspectRatio = AspectRatio.ORIGINAL)).outputSize)
    }

    @Test
    fun previewSpec_differsOnlyInOutputAndTextPixelSize() {
        val text = TextOverlay("t", "Hi", 0L, 1_000L)
        val p = project(videoClip("a")).copy(textOverlays = listOf(text))
        val export = compositionSpecOf(p)
        val preview = compositionSpecOf(p, shortSidePx = 720)
        assertEquals(OutputSize(720, 1280), preview.outputSize)
        assertEquals(export.items, preview.items)
        assertEquals(export.audioLanes, preview.audioLanes)
        assertEquals(export.texts.single().textSizePx * 720 / 1080, preview.texts.single().textSizePx, 0.01f)
        assertEquals(export.texts.single().copy(textSizePx = 0f), preview.texts.single().copy(textSizePx = 0f))
    }
}
