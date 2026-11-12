package com.savie.precut.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The 2026 tokens' contrast (RD-TOK): text at least 4.5 : 1 (WCAG AA) on every surface it's
 * drawn on, labels on track bars and on the pink and lime fills at least 6 : 1, and faint ticks
 * at least 3 : 1. A token change that makes something unreadable fails here.
 */
class ColorContrastTest {

    @Test
    fun `text is readable on every surface`() {
        listOf(Ink0, Ink1, Ink2, Ink3).forEach { surface ->
            assertAtLeast("TextPrimary", TextPrimary, surface, 4.5)
            assertAtLeast("TextSecondary", TextSecondary, surface, 4.5)
            assertAtLeast("TextMuted", TextMuted, surface, 4.5)
            assertAtLeast("Accent text", Accent, surface, 4.5)
            assertAtLeast("AI text", AiAccent, surface, 4.5)
            assertAtLeast("Error text", ErrorRed, surface, 4.5)
        }
        assertAtLeast("TextPrimary on a picked card", TextPrimary, SelectedSurface, 4.5)
        assertAtLeast("selected chip label", OnAccentContainer, AccentContainer, 4.5)
        assertAtLeast("pink on a selected chip", Accent, AccentContainer, 4.5)
        assertAtLeast("AI chip", AiAccent, AiContainer, 4.5)
        assertAtLeast("error banner", OnErrorContainer, ErrorContainer, 4.5)
        assertAtLeast("light toast", Ink0, InverseSurface, 4.5)
        assertAtLeast("light toast action", InverseAccent, InverseSurface, 4.5)
        assertAtLeast("pressed main pill", OnAccent, AccentFillPressed, 4.5)
    }

    @Test
    fun `labels on pink, lime and track bars reach 6 to 1`() {
        assertAtLeast("main pill", OnAccent, AccentFill, 6.0)
        assertAtLeast("AI fill", OnAiAccent, AiAccent, 6.0)
        listOf(
            "video" to VideoBar, "music" to MusicBar, "voiceover" to VoiceoverBar, "extracted audio" to ExtractedAudioBar,
            "text" to TextLaneBar, "overlay" to OverlayBar, "sticker" to StickerBar, "effect" to EffectBar, "look" to LookLayerBar,
        ).forEach { (name, bar) -> assertAtLeast("$name bar label", TrackLabel, bar, 6.0) }
    }

    @Test
    fun `faint ticks stay visible and effects aren't drawn in the AI colour`() {
        assertAtLeast("TextFaint", TextFaint, Ink0, 3.0)
        assertAtLeast("ruler ticks", RulerTick, Ink0, 3.0)
        assertTrue("lime is for AI only", EffectBar != AiAccent)
    }

    private fun assertAtLeast(name: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrast(foreground, background)
        assertTrue("$name: ${"%.2f".format(ratio)} : 1, needs $minimum", ratio >= minimum)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    /** WCAG relative luminance of an opaque sRGB colour. */
    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
