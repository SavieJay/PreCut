package com.savie.precut.media

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.AudioClip
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.look.LookLayer
import com.savie.precut.model.VoiceEffect
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.ClipType
import com.savie.precut.model.DEFAULT_IMAGE_DURATION_MS
import com.savie.precut.model.OutputSize
import com.savie.precut.model.CoverSource
import com.savie.precut.model.ProjectCover
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.clipFromMedia
import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.OverlayClip
import com.savie.precut.model.project
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.model.speed.SpeedPreset
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
    fun speedLookAndRotation_areCarriedPerItem() {
        val look = ClipLook(filterId = "basic.mono")
        val clip = videoClip("a").copy(speed = 2f, look = look, rotationDegrees = 90)
        val item = compositionSpecOf(project(clip)).items.single() as ItemSpec.Video
        assertEquals(2f, item.speed)
        assertEquals(VisualSpec(look, rotationDegrees = 90), item.visual)
    }

    @Test
    fun identityLook_addsNoGrading_andLayersCarryTimelineMicros() {
        val clip = videoClip("a").copy(look = ClipLook(filterId = "basic.mono", filterIntensity = 0f))
        assertEquals(null, (compositionSpecOf(project(clip)).items.single() as ItemSpec.Video).visual.look)
        val warm = ClipLook(filterId = "basic.warm")
        val p = project(videoClip("a")).copy(
            lookLayers = listOf(LookLayer("l", 1_000L, 2_500L, warm), LookLayer("empty", 0L, 500L)),
        )
        assertEquals(listOf(LookLayerSpec(1_000_000L, 2_500_000L, warm)), compositionSpecOf(p).lookLayers)
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

    @Test
    fun plainClip_usesTheCheapPath() {
        val visual = compositionSpecOf(project(videoClip("a"))).items.single().visual
        assertEquals(VisualSpec(), visual)
    }

    @Test
    fun frameEdits_mapToVisualSpec() {
        val crop = CropRect(0.1f, 0.2f, 0.9f, 0.8f)
        val clip = videoClip("a").copy(
            mirrored = true, rotationDegrees = 90, crop = crop, opacity = 0.5f,
            transform = ClipTransform(scale = 2f), canvas = ClipCanvas(kind = CanvasKind.BLUR, blur = 3),
        )
        val visual = compositionSpecOf(project(clip)).items.single().visual
        assertTrue(visual.mirrored)
        assertEquals(90, visual.rotationDegrees)
        assertEquals(crop, visual.crop)
        assertEquals(FrameSpec(FrameFit.FIT, ClipTransform(scale = 2f), 0.5f, ClipCanvas(kind = CanvasKind.BLUR, blur = 3)), visual.frame)
        // A crop of everything is no crop.
        assertEquals(null, compositionSpecOf(project(videoClip("b").copy(crop = CropRect.FULL))).items.single().visual.crop)
    }

    @Test
    fun liveClip_getsAFrameSpecKeyedByItsId_onlyInPreview() {
        val p = project(videoClip("a"), videoClip("b").copy(fit = FrameFit.FILL))
        val preview = compositionSpecOf(p, liveClipId = "a").items.map { it.visual.frame }
        assertEquals(FrameSpec(liveKey = "a"), preview[0])
        assertEquals(FrameSpec(fit = FrameFit.FILL), preview[1])
        val export = compositionSpecOf(p).items.map { it.visual.frame }
        assertEquals(listOf(null, FrameSpec(fit = FrameFit.FILL)), export)
    }

    @Test
    fun transitions_mapToBothSidesOfTheJunction() {
        val p = project(
            videoClip("a", 10_000L).copy(transitionOut = ClipTransition(TransitionType.SPIN, 1_000L)),
            videoClip("b", 600L),
            videoClip("c", 10_000L).copy(transitionOut = ClipTransition(TransitionType.FLASH)),
        )
        val visuals = compositionSpecOf(p).items.map { it.visual }
        assertEquals(TransitionSpec(ClipTime(0L, 10_000L), tail = TransitionType.SPIN, tailMs = 600L), visuals[0].transition)
        assertEquals(TransitionSpec(ClipTime(10_000_000L, 600L), head = TransitionType.SPIN, headMs = 600L), visuals[1].transition)
        // The last clip's transition has nothing to go to.
        assertEquals(null, visuals[2].transition)
    }

    @Test
    fun animations_needTheCanvasPath() {
        val fade = ClipAnimation(AnimationType.FADE)
        val p = project(videoClip("a"), videoClip("b").copy(animationIn = fade))
        val frames = compositionSpecOf(p).items.map { it.visual.frame }
        assertEquals(null, frames[0])
        assertEquals(AnimationSpec(ClipTime(10_000_000L, 10_000L), animationIn = fade), frames[1]?.animation)
    }

    @Test
    fun speedCurve_isPassedToTheVideoItem() {
        val curve = SpeedCurve.preset(SpeedPreset.MONTAGE)
        val item = compositionSpecOf(project(videoClip("a").copy(speedCurve = curve))).items.single() as ItemSpec.Video
        assertEquals(curve, item.speedCurve)
    }

    @Test
    fun overlays_mapToPaddedRows_onTransparentFrames() {
        val p = project(videoClip("a", 10_000L)).copy(
            overlays = listOf(
                OverlayClip(videoClip("o", 3_000L), timelineStartMs = 2_000L),
                OverlayClip(videoClip("late", 6_000L), timelineStartMs = 8_000L),
                OverlayClip(videoClip("after"), timelineStartMs = 10_000L),
            ),
        )
        val spec = compositionSpecOf(p, liveClipId = "o")
        assertEquals(10_000_000L, spec.durationUs)
        val row = spec.overlayLanes.single()
        assertEquals(listOf(2_000_000L, 3_000_000L), row.items.map { it.gapBeforeUs })
        assertEquals(listOf(2_000_000L until 5_000_000L, 8_000_000L until 10_000_000L), row.items.map { it.startUs until it.endUs })
        assertEquals(0L, row.trailingGapUs)
        val first = row.items[0].item.visual.frame
        assertTrue(first?.transparent == true)
        assertEquals("o", first?.liveKey)
        // Cut at the video's end.
        assertEquals(2_000L, (row.items[1].item as ItemSpec.Video).clipEndMs)
    }

    @Test
    fun animatedOverlay_timesFromItsStart() {
        val fade = ClipAnimation(AnimationType.FADE)
        val p = project(videoClip("a")).copy(overlays = listOf(OverlayClip(videoClip("o", 3_000L).copy(animationIn = fade), 4_000L)))
        val frame = compositionSpecOf(p).overlayLanes.single().items.single().item.visual.frame
        assertEquals(AnimationSpec(ClipTime(4_000_000L, 3_000L), animationIn = fade), frame?.animation)
    }

    @Test
    fun layerCompositor_hidesRowsOutsideTheirOverlays() {
        val settings = LayerCompositorSettings(OutputSize(1080, 1920), listOf(emptyList(), listOf(2_000_000L until 5_000_000L), null))
        assertEquals(false, settings.isVisible(0, 3_000_000L))
        assertEquals(true, settings.isVisible(1, 3_000_000L))
        assertEquals(false, settings.isVisible(1, 6_000_000L))
        assertEquals(true, settings.isVisible(2, 6_000_000L))
    }
}
