package com.savie.precut.model.text

import com.savie.precut.media.textSizePx
import com.savie.precut.media.textSpecFor
import com.savie.precut.model.OutputSize
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.withStyleOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextStyleTest {
    private val words = TextOverlay("t", "Hi there", 1_000L, 4_000L, xNorm = 0.2f, yNorm = 0.3f, rotationDegrees = 15f)

    @Test
    fun withStyleOf_copiesTheLookOnly() {
        val look = TextTemplates.all.first { it.id == "retro" }.style
        val styled = words.withStyleOf(look)
        assertEquals(look.fontId, styled.fontId)
        assertEquals(look.stroke, styled.stroke)
        assertEquals(look.curve, styled.curve)
        // Words, timing and place stay.
        assertEquals(listOf("Hi there", 1_000L, 4_000L, 0.2f, 0.3f, 15f), listOf(styled.text, styled.startMs, styled.endMs, styled.xNorm, styled.yNorm, styled.rotationDegrees))
    }

    @Test
    fun templates_haveUniqueIdsAndBundledFonts() {
        val ids = TextTemplates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(TextTemplates.all.all { it.style.fontId == null || BundledFont.of(it.style.fontId) != null })
        assertTrue(TextTemplates.all.all { it.applyTo(words).text == "Hi there" })
    }

    @Test
    fun textSpecFor_carriesTheStyleAndScalesToTheShortSide() {
        val styled = words.copy(textSizeSp = 36f, stroke = TextStroke(width = 2f), animationIn = TextAnimation(TextAnimationType.POP))
        val spec = textSpecFor(styled, OutputSize(1080, 1920))
        assertEquals(styled, spec.style)
        assertEquals(textSizePx(36f, 1080), spec.textSizePx)
        assertEquals(108f, spec.textSizePx)
        assertEquals(1_000_000L to 4_000_000L, spec.startUs to spec.endUs)
        // x 0.2 → NDC -0.6; y 0.3 (down) → NDC 0.4 (up).
        assertEquals(-0.6f, spec.anchorX, 1e-5f)
        assertEquals(0.4f, spec.anchorY, 1e-5f)
    }
}
