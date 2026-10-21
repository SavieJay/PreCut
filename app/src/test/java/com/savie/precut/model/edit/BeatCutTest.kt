package com.savie.precut.model.edit

import com.savie.precut.model.AudioClip
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.MAX_IMAGE_DURATION_MS
import com.savie.precut.model.Project
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.project
import com.savie.precut.model.speed.SpeedCurve
import com.savie.precut.model.speed.SpeedPoint
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BeatCutTest {

    private fun photo(id: String, durationMs: Long = 3_000L) =
        Clip(id = id, uri = "content://media/$id", type = ClipType.IMAGE, sourceDurationMs = MAX_IMAGE_DURATION_MS, trimEndMs = durationMs)

    /** A 20 s song at [startMs] with a beat every [everyMs] (source time). */
    private fun Project.withMusic(startMs: Long = 0L, everyMs: Long = 500L, speed: Float = 1f, lengthMs: Long = 20_000L) = copy(
        audioClips = listOf(
            AudioClip(
                id = "m", uri = "content://music", sourceDurationMs = lengthMs, timelineStartMs = startMs, trimEndMs = lengthMs,
                speed = speed, beatsMs = (everyMs..lengthMs step everyMs).toList(),
            ),
        ),
    )

    private fun cut(project: Project): Pair<Project, BeatCutPlan> {
        val plan = project.beatCutPlan("m")
        return EditReducer.reduce(project, plan.edit) to plan
    }

    /** Where each clip ends on the timeline. */
    private fun Project.ends() = clipStartTimesMs().zip(clips) { start, clip -> start + clip.timelineDurationMs() }

    @Test
    fun `every clip ends on the next beat, keeping its in point`() {
        val (result, plan) = cut(project(videoClip("a", trimStartMs = 1_000), videoClip("b"), videoClip("c")).withMusic())
        assertEquals(listOf(500L, 1_000L, 1_500L), result.ends())
        assertEquals(1_000L, result.clips[0].trimStartMs)
        assertEquals(3, plan.cut)
        assertEquals(0, plan.leftAlone)
    }

    @Test
    fun `photos fit exactly, never under their minimum`() {
        val (result, _) = cut(project(photo("a"), photo("b")).withMusic(everyMs = 400))
        // 400 ms is under a photo's 500 ms minimum, so each photo takes the next beat after it.
        assertEquals(listOf(800L, 1_600L), result.ends())
    }

    @Test
    fun `speed counts, a 2x clip uses twice the media`() {
        val (result, _) = cut(project(videoClip("a", speed = 2f)).withMusic())
        assertEquals(1_000L, result.clips[0].trimEndMs)
        assertEquals(500L, result.ends().single())
    }

    @Test
    fun `a clip too short for the next beat stays, and the next one lands on a beat again`() {
        val (result, plan) = cut(project(videoClip("short", sourceDurationMs = 300), videoClip("b")).withMusic())
        assertEquals(300L, result.clips[0].timelineDurationMs())
        assertEquals(listOf(300L, 500L), result.ends())
        assertEquals(1, plan.leftAlone)
    }

    @Test
    fun `a speed-curved clip is left as it was`() {
        val curved = videoClip("a").copy(speedCurve = SpeedCurve(listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 2f))))
        val start = project(curved, videoClip("b")).withMusic()
        val (result, plan) = cut(start)
        assertEquals(start.clips[0], result.clips[0])
        assertEquals(1, plan.leftAlone)
    }

    @Test
    fun `beats are in timeline time, so music start and speed count`() {
        // Music placed at 2 s, played at 2x: beats every 500 ms of source are every 250 ms from 2.25 s.
        val (result, _) = cut(project(videoClip("a"), videoClip("b")).withMusic(startMs = 2_000, speed = 2f))
        assertEquals(listOf(2_250L, 2_500L), result.ends())
    }

    @Test
    fun `clips after the music's last beat stay as they are`() {
        val start = project(videoClip("a"), videoClip("b"), videoClip("c")).withMusic(lengthMs = 1_000)
        val (result, plan) = cut(start)
        assertEquals(listOf(500L, 1_000L), result.ends().take(2))
        assertEquals(start.clips[2], result.clips[2])
        assertEquals(2, plan.cut)
    }

    @Test
    fun `no beats or no such music changes nothing`() {
        val start = project(videoClip("a")).withMusic().let { it.copy(audioClips = it.audioClips.map { a -> a.copy(beatsMs = emptyList()) }) }
        assertSame(start, EditReducer.reduce(start, start.beatCutPlan("m").edit))
        assertEquals(0, start.beatCutPlan("missing").cut)
    }

    @Test
    fun `set beats replaces them, sorted, within the trimmed range`() {
        val start = project(videoClip("a", sourceDurationMs = 30_000)).withMusic()
        val result = EditReducer.reduce(start, EditAction.SetBeats("m", listOf(1_500, 500, 500, 25_000)))
        assertEquals(listOf(500L, 1_500L), result.audioClips.single().beatsMs)
    }

    @Test
    fun `from the middle, videos are centred on their media`() {
        val start = project(videoClip("a", trimStartMs = 1_000, trimEndMs = 2_000), photo("b")).withMusic()
        val plan = start.beatCutPlan("m", fromMiddle = true)
        val result = EditReducer.reduce(start, plan.edit)
        // 10 s video, 500 ms piece: 4.75 s to 5.25 s, whatever its old trim was.
        assertEquals(4_750L, result.clips[0].trimStartMs)
        assertEquals(5_250L, result.clips[0].trimEndMs)
        assertEquals(listOf(500L, 1_000L), result.ends())
    }

    @Test
    fun `autoCut adds the music at the start, marks its beats and cuts every clip`() {
        val music = AudioClip(id = "song", uri = "content://song", sourceDurationMs = 20_000, timelineStartMs = 7_000, trimEndMs = 20_000)
        val result = autoCutProject(project(videoClip("a"), videoClip("b"), photo("c")), music, listOf(400L, 800L, 1_200L, 1_600L))
        val added = result.audioClips.single()
        assertEquals(0L, added.timelineStartMs)
        assertEquals(listOf(400L, 800L, 1_200L, 1_600L), added.beatsMs)
        // Photos need 500 ms, so the photo after 800 ms takes the beat after 1 300 ms.
        assertEquals(listOf(400L, 800L, 1_600L), result.ends())
    }

    @Test
    fun `autoCut without beats keeps the clips and still adds the music`() {
        val music = AudioClip(id = "song", uri = "content://song", sourceDurationMs = 20_000, trimEndMs = 20_000)
        val clips = project(videoClip("a"), videoClip("b"))
        val result = autoCutProject(clips, music, emptyList())
        assertEquals(clips.clips, result.clips)
        assertEquals("song", result.audioClips.single().id)
    }
}
