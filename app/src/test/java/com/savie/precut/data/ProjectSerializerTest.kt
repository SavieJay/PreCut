package com.savie.precut.data

import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.frame.CanvasPattern
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.CropRatio
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.OverlayClip
import com.savie.precut.model.ProjectCover
import com.savie.precut.model.ReverseSource
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.model.speed.SpeedPreset
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.motion.ComboType
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectSerializerTest {

    @Test
    fun roundTrip() {
        val original = project(videoClip("a")).copy(name = "Trip", originalAudioMuted = true)
        assertEquals(original, ProjectSerializer.decode(ProjectSerializer.encode(original)))
    }

    @Test
    fun unknownKeysAreIgnored_andMissingOnesTakeDefaults() {
        val json = """{"id":"p","name":"Old","createdAt":1,"updatedAt":2,"futureField":42}"""
        val project = ProjectSerializer.decode(json)
        assertEquals("Old", project?.name)
        assertEquals(emptyList<Any>(), project?.clips)
    }

    @Test
    fun phase6Draft_loadsWithPhase7Defaults() {
        val json = """{"id":"p","name":"Old","aspectRatio":"RATIO_16_9","createdAt":1,"updatedAt":2,""" +
            """"clips":[{"id":"a","uri":"content://a","type":"VIDEO","sourceDurationMs":5000,"trimEndMs":5000}],""" +
            """"textOverlays":[{"id":"t","text":"Hi","startMs":0,"endMs":1000}]}"""
        val project = ProjectSerializer.decode(json)
        assertEquals(true, project?.clips?.single()?.keepPitch)
        assertEquals(0f, project?.textOverlays?.single()?.rotationDegrees)
        assertEquals(1f, project?.textOverlays?.single()?.opacity)
        assertNull(project?.cover)
    }

    @Test
    fun phase8ADraft_loadsWithPlainFrame_andFrameRoundTrips() {
        val json = """{"id":"p","name":"Old","createdAt":1,"updatedAt":2,""" +
            """"clips":[{"id":"a","uri":"content://a","type":"VIDEO","sourceDurationMs":5000,"trimEndMs":5000,"rotationDegrees":90}]}"""
        val clip = ProjectSerializer.decode(json)?.clips?.single()
        assertEquals(90, clip?.rotationDegrees)
        assertEquals(false, clip?.mirrored)
        assertNull(clip?.crop)
        assertEquals(FrameFit.FIT, clip?.fit)
        assertEquals(ClipTransform.IDENTITY, clip?.transform)
        assertEquals(1f, clip?.opacity)
        assertEquals(ClipCanvas(), clip?.canvas)
        assertNull(clip?.transitionOut)
        assertNull(clip?.animationCombo)
        assertNull(clip?.speedCurve)
        assertNull(clip?.reversedFrom)
        assertEquals(emptyList<Any>(), ProjectSerializer.decode(json)?.overlays)

        val framed = project(
            videoClip("b").copy(
                mirrored = true, crop = CropRect(0.1f, 0.2f, 0.9f, 0.8f), cropRatio = CropRatio.RATIO_1_1, fit = FrameFit.FILL,
                transform = ClipTransform(1.5f, -30f, 0.1f, -0.2f), opacity = 0.4f,
                canvas = ClipCanvas(kind = CanvasKind.IMAGE, pattern = CanvasPattern.DUSK),
                transitionOut = ClipTransition(TransitionType.GLITCH, 800L), animationIn = ClipAnimation(AnimationType.BOUNCE),
                animationCombo = ComboAnimation(ComboType.SHAKE, 1.5f),
                speedCurve = SpeedCurve.preset(SpeedPreset.HERO),
                reversedFrom = ReverseSource("content://orig", 9_000L, 1_000L, 5_000L, 1920, 1080, 90),
            ),
        )
        assertEquals(framed, ProjectSerializer.decode(ProjectSerializer.encode(framed)))
        val withOverlay = framed.copy(overlays = listOf(OverlayClip(videoClip("pip").copy(opacity = 0.5f), 1_500L)))
        assertEquals(withOverlay, ProjectSerializer.decode(ProjectSerializer.encode(withOverlay)))
    }

    @Test
    fun phase7Filter_migratesToLook() {
        val json = """{"id":"p","name":"Old","createdAt":1,"updatedAt":2,""" +
            """"clips":[{"id":"a","uri":"content://a","type":"VIDEO","sourceDurationMs":5000,"trimEndMs":5000,"filter":"WARM"},""" +
            """{"id":"b","uri":"content://b","type":"VIDEO","sourceDurationMs":5000,"trimEndMs":5000}]}"""
        val clips = ProjectSerializer.decode(json)?.clips
        assertEquals("basic.warm", clips?.get(0)?.look?.filterId)
        assertEquals(1f, clips?.get(0)?.look?.filterIntensity)
        assertNull(clips?.get(1)?.look?.filterId)
        // Saved again, it uses the new format only.
        val again = ProjectSerializer.encode(requireNotNull(ProjectSerializer.decode(json)))
        assertEquals(false, again.contains("\"filter\""))
    }

    @Test
    fun cover_roundTrips() {
        val frame = project(videoClip("a")).copy(cover = ProjectCover.Frame(1_200L), coverInExport = true)
        assertEquals(frame, ProjectSerializer.decode(ProjectSerializer.encode(frame)))
        val photo = frame.copy(cover = ProjectCover.Image("content://p", 90))
        assertEquals(photo, ProjectSerializer.decode(ProjectSerializer.encode(photo)))
    }

    @Test
    fun corruptJson_isNull() {
        assertNull(ProjectSerializer.decode("{not json"))
        assertNull(ProjectSerializer.decode("""{"id":"p"}""")) // required fields missing
    }
}
