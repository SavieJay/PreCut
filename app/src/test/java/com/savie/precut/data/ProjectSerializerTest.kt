package com.savie.precut.data

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
    fun corruptJson_isNull() {
        assertNull(ProjectSerializer.decode("{not json"))
        assertNull(ProjectSerializer.decode("""{"id":"p"}""")) // required fields missing
    }
}
