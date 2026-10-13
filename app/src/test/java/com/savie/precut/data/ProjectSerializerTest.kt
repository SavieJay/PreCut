package com.savie.precut.data

import com.savie.precut.model.ProjectCover
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
