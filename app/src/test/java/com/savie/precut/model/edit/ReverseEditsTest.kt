package com.savie.precut.model.edit

import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import com.savie.precut.model.ReverseSource
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReverseEditsTest {

    // A 20 s source trimmed to 4..10 s, reversed into a 6 s file.
    private val original = videoClip("a", 20_000L, trimStartMs = 4_000L, trimEndMs = 10_000L).copy(sourceWidth = 1920, sourceHeight = 1080, sourceRotationDegrees = 90)
    private val base = project(original)
    private val info = MediaInfo(ClipType.VIDEO, 6_000L, 1080, 1920, 0)
    private val reversed = EditReducer.reduce(base, EditAction.ReverseClip("a", "file:///reversed/r.mp4", info))

    @Test
    fun reverse_playsTheCopy_andRemembersTheOriginal() {
        val clip = reversed.clips[0]
        assertEquals("file:///reversed/r.mp4", clip.uri)
        assertEquals(0L to 6_000L, clip.trimStartMs to clip.trimEndMs)
        assertEquals(0, clip.sourceRotationDegrees)
        assertEquals(ReverseSource(original.uri, 20_000L, 4_000L, 10_000L, 1920, 1080, 90), clip.reversedFrom)
        // Reversing twice in a row does nothing; photos can't be reversed.
        assertSame(reversed, EditReducer.reduce(reversed, EditAction.ReverseClip("a", "file:///other.mp4", info)))
        val photo = project(videoClip("p").copy(type = ClipType.IMAGE))
        assertSame(photo, EditReducer.reduce(photo, EditAction.ReverseClip("p", "file:///r.mp4", info)))
    }

    @Test
    fun unreverse_restoresTheOriginal() {
        assertEquals(original, EditReducer.reduce(reversed, EditAction.UnreverseClip("a")).clips[0])
    }

    @Test
    fun unreverse_mapsATrimOfTheReversedCopy() {
        // Keeping 1..3 s of the reversed copy is 13..15 s from its end: original 7..9 s.
        val trimmed = EditReducer.reduce(reversed, EditAction.TrimClip("a", 1_000L, 3_000L))
        val back = EditReducer.reduce(trimmed, EditAction.UnreverseClip("a")).clips[0]
        assertEquals(7_000L to 9_000L, back.trimStartMs to back.trimEndMs)
        assertNull(back.reversedFrom)
    }
}
