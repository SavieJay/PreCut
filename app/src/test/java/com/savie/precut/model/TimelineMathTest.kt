package com.savie.precut.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineMathTest {

    @Test
    fun timelineDuration_appliesTrimAndSpeed() {
        assertEquals(10_000L, videoClip().timelineDurationMs())
        assertEquals(6_000L, videoClip(trimStartMs = 1_000L, trimEndMs = 7_000L).timelineDurationMs())
        assertEquals(3_000L, videoClip(trimStartMs = 1_000L, trimEndMs = 7_000L, speed = 2f).timelineDurationMs())
        assertEquals(24_000L, videoClip(trimEndMs = 6_000L, speed = 0.25f).timelineDurationMs())
    }

    @Test
    fun timelineDuration_roundsToNearestMs() {
        // 1000 / 3 = 333.33 ms
        assertEquals(333L, videoClip(trimEndMs = 1_000L, speed = 3f).timelineDurationMs())
    }

    @Test
    fun totalDuration_sumsClips_andIsZeroWhenEmpty() {
        assertEquals(0L, project().totalDurationMs())
        val p = project(videoClip("a", 4_000L), videoClip("b", 6_000L, speed = 2f))
        assertEquals(7_000L, p.totalDurationMs())
    }

    @Test
    fun clipStartTimes_areCumulative() {
        val p = project(videoClip("a", 4_000L), videoClip("b", 6_000L, speed = 2f), videoClip("c", 1_000L))
        assertEquals(listOf(0L, 4_000L, 7_000L), p.clipStartTimesMs())
        assertEquals(emptyList<Long>(), project().clipStartTimesMs())
    }

    @Test
    fun clipAt_findsClipAndOffset() {
        val p = project(videoClip("a", 4_000L), videoClip("b", 6_000L))
        assertEquals(ClipPosition(0, 0L), p.clipAt(0L))
        assertEquals(ClipPosition(0, 3_999L), p.clipAt(3_999L))
        assertEquals(ClipPosition(1, 2_500L), p.clipAt(6_500L))
    }

    @Test
    fun clipAt_boundaryBelongsToNextClip() {
        val p = project(videoClip("a", 4_000L), videoClip("b", 6_000L))
        assertEquals(ClipPosition(1, 0L), p.clipAt(4_000L))
    }

    @Test
    fun clipAt_endOfTimelineMapsToEndOfLastClip() {
        val p = project(videoClip("a", 4_000L), videoClip("b", 6_000L))
        assertEquals(ClipPosition(1, 6_000L), p.clipAt(10_000L))
    }

    @Test
    fun clipAt_returnsNullOutsideTimeline() {
        val p = project(videoClip("a", 4_000L))
        assertNull(p.clipAt(-1L))
        assertNull(p.clipAt(4_001L))
        assertNull(project().clipAt(0L))
    }

    @Test
    fun timelineToSource_appliesTrimAndSpeed() {
        val clip = videoClip(trimStartMs = 2_000L, trimEndMs = 8_000L, speed = 2f)
        assertEquals(2_000L, clip.timelineOffsetToSourceMs(0L))
        assertEquals(4_000L, clip.timelineOffsetToSourceMs(1_000L))
        assertEquals(8_000L, clip.timelineOffsetToSourceMs(3_000L))
    }

    @Test
    fun timelineToSource_clampsToTrimRange() {
        val clip = videoClip(trimStartMs = 2_000L, trimEndMs = 8_000L)
        assertEquals(2_000L, clip.timelineOffsetToSourceMs(-500L))
        assertEquals(8_000L, clip.timelineOffsetToSourceMs(99_000L))
    }

    @Test
    fun sourceToTimeline_isInverseOfTimelineToSource() {
        val clip = videoClip(trimStartMs = 1_000L, trimEndMs = 9_000L, speed = 0.5f)
        for (offset in listOf(0L, 1_234L, 8_000L, 16_000L)) {
            assertEquals(offset, clip.sourceToTimelineOffsetMs(clip.timelineOffsetToSourceMs(offset)))
        }
    }

    @Test
    fun sourceToTimeline_clampsOutsideTrim() {
        val clip = videoClip(trimStartMs = 1_000L, trimEndMs = 5_000L)
        assertEquals(0L, clip.sourceToTimelineOffsetMs(0L))
        assertEquals(4_000L, clip.sourceToTimelineOffsetMs(9_000L))
    }

    @Test
    fun clipFromMedia_imagesGetDefaultDuration() {
        val image = clipFromMedia("i", "content://i", MediaInfo(ClipType.IMAGE, 0L, 100, 200, 90))
        assertEquals(DEFAULT_IMAGE_DURATION_MS, image.sourceDurationMs)
        assertEquals(DEFAULT_IMAGE_DURATION_MS, image.trimEndMs)
        assertEquals(90, image.sourceRotationDegrees)

        val video = clipFromMedia("v", "content://v", MediaInfo(ClipType.VIDEO, 5_500L, 1920, 1080, 0))
        assertEquals(5_500L, video.trimEndMs)
        assertEquals(1920, video.sourceWidth)
    }
}
