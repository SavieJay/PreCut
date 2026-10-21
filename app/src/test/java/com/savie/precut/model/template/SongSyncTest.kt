package com.savie.precut.model.template

import com.savie.precut.model.AudioClip
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.clipStartTimesMs
import com.savie.precut.model.project
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.videoClip
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSyncTest {
    // Three 2 s slots; a title over the first clip and a caption over the middle of the third.
    private val template = project(videoClip("a", trimEndMs = 2_000), videoClip("b", trimEndMs = 2_000), videoClip("c", trimEndMs = 2_000)).copy(
        textOverlays = listOf(TextOverlay(id = "title", text = "T", startMs = 0, endMs = 2_000), TextOverlay(id = "end", text = "E", startMs = 4_500, endMs = 5_500)),
    ).toTemplate("t", "T", TemplateCategory.OTHER, setOf("a", "b", "c"), emptySet(), 0)

    private val song = AudioClip(id = "song", uri = "content://song", sourceDurationMs = 30_000, trimEndMs = 30_000)

    @Test
    fun `the pace follows the template's own slot length`() {
        // 2 s slots: 4 beats at 120 BPM (0.5 s each), 2 at 60 BPM, capped at 8 for very fast songs.
        assertEquals(4, template.beatsPerCut(120f))
        assertEquals(2, template.beatsPerCut(60f))
        assertEquals(MAX_BEATS_PER_CUT, template.beatsPerCut(400f))
        assertEquals(1, template.beatsPerCut(0f))
    }

    @Test
    fun `slots land on the beats and texts move with their clip`() {
        val filled = template.project.copy(clips = template.project.clips.map { it.copy(uri = "content://v", sourceDurationMs = 10_000) })
        // A beat every 1.5 s: clips become 1.5 s each.
        val synced = filled.syncedToSong(song, (1_500L..30_000L step 1_500L).toList())
        assertEquals(listOf(0L, 1_500L, 3_000L), synced.clipStartTimesMs())
        assertEquals(4_500L, synced.totalDurationMs())
        assertEquals(0L to 1_500L, synced.textOverlays[0].let { it.startMs to it.endMs })
        // 4.5–5.5 s was the middle quarter of the third clip (4–6 s): now 3.375–4.125 s.
        assertEquals(3_375L to 4_125L, synced.textOverlays[1].let { it.startMs to it.endMs })
        assertEquals("song", synced.audioClips.single().id)
    }

    @Test
    fun `without beats only the song is added`() {
        val synced = template.project.syncedToSong(song, emptyList())
        assertEquals(template.project.clips, synced.clips)
        assertEquals(1, synced.audioClips.size)
    }

    @Test
    fun `a beat right at the start is skipped, so the first clip doesn't just flash`() {
        assertEquals(listOf(4_500L, 8_500L), listOf(500L, 4_500L, 8_500L).withoutEarlyBeats())
        assertEquals(listOf(1_500L, 3_000L), listOf(1_500L, 3_000L).withoutEarlyBeats())
        assertEquals(listOf(700L), listOf(700L).withoutEarlyBeats())
    }
}
