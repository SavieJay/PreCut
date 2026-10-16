package com.savie.precut.model.text

import com.savie.precut.model.AudioKind
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.timelineDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMotionTest {
    private val text = TextOverlay("t", "Hello", 0L, 3_000L)

    @Test
    fun noAnimation_isStill() {
        assertEquals(TextMotion(), TextMotions.at(text, 500L, 3_000L))
        assertNull(TextMotions.revealAt(text, 5, 500L, 3_000L))
    }

    @Test
    fun fadeIn_goesFromHiddenToShown() {
        val fading = text.copy(animationIn = TextAnimation(TextAnimationType.FADE, 1_000L))
        assertEquals(0f, TextMotions.at(fading, 0L, 3_000L).alpha)
        val half = TextMotions.at(fading, 500L, 3_000L).alpha
        assertTrue(half in 0.5f..1f) // Eased out: quick start, gentle end.
        assertEquals(1f, TextMotions.at(fading, 1_000L, 3_000L).alpha)
    }

    @Test
    fun out_playsTheEntranceBackwards() {
        val sliding = text.copy(animationOut = TextAnimation(TextAnimationType.SLIDE, 1_000L))
        assertEquals(TextMotion(), TextMotions.at(sliding, 1_500L, 3_000L))
        val leaving = TextMotions.at(sliding, 2_750L, 3_000L)
        assertEquals(TextMotions.entrance(TextAnimation(TextAnimationType.SLIDE, 1_000L), 0.25f), leaving)
        assertTrue(leaving.dy < 0f && leaving.alpha < 1f)
    }

    @Test
    fun pop_overshootsThenSettles() {
        val pop = TextAnimation(TextAnimationType.POP, 1_000L)
        assertEquals(0f, TextMotions.entrance(pop, 0f).scale)
        assertEquals(1.15f, TextMotions.entrance(pop, 0.6f).scale, 1e-5f)
        assertEquals(1f, TextMotions.entrance(pop, 1f).scale, 1e-5f)
    }

    @Test
    fun loops_repeatEachCycle() {
        val wave = text.copy(animationLoop = TextAnimation(TextAnimationType.WAVE, 2_000L))
        assertEquals(TextMotions.at(wave, 300L, 10_000L), TextMotions.at(wave, 4_300L, 10_000L))
        assertEquals(0f, TextMotions.at(wave, 0L, 10_000L).rotationDegrees, 1e-5f)
    }

    @Test
    fun typewriter_revealsCharactersOverItsDuration() {
        val typing = text.copy(animationIn = TextAnimation(TextAnimationType.TYPEWRITER, 1_000L))
        assertEquals(0, TextMotions.revealAt(typing, 5, 0L, 3_000L))
        assertEquals(3, TextMotions.revealAt(typing, 5, 500L, 3_000L))
        assertNull(TextMotions.revealAt(typing, 5, 1_000L, 3_000L))
        // Typewriter doesn't move the text.
        assertEquals(TextMotion(), TextMotions.at(typing, 500L, 3_000L))
    }

    @Test
    fun voiceover_startsWithTheTextAndLastsTheSpeech() {
        val clip = text.copy(startMs = 2_500L).voiceoverClip("v", "file:///voiceovers/tts.wav", 1_051L)
        assertEquals(2_500L, clip.timelineStartMs)
        assertEquals(1_051L, clip.timelineDurationMs())
        assertEquals(AudioKind.VOICEOVER, clip.kind)
    }
}
