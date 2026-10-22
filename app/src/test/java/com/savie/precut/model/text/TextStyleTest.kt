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
    fun templates_fillEveryGroup_andStayInTheEditorsRanges() {
        TextTemplateCategory.entries.forEach { category ->
            // At most three rows of three tiles, so a group never needs scrolling.
            assertTrue(category.name, TextTemplates.inCategory(category).size in 1..9)
        }
        TextTemplates.all.forEach { template ->
            val style = template.style
            assertTrue(template.id, style.letterSpacing in -5f..20f)
            assertTrue(template.id, style.curve in -MAX_TEXT_CURVE..MAX_TEXT_CURVE)
            assertTrue(template.id, style.textSizeSp in com.savie.precut.model.MIN_TEXT_SIZE_SP..com.savie.precut.model.MAX_TEXT_SIZE_SP)
            listOfNotNull(style.animationIn, style.animationOut, style.animationLoop).forEach {
                assertTrue(template.id, it.durationMs in MIN_TEXT_ANIMATION_MS..MAX_TEXT_ANIMATION_MS)
            }
        }
        // Every Phase 19 font shows up in at least one template.
        val used = TextTemplates.all.mapNotNull { BundledFont.of(it.style.fontId) }.toSet()
        val phase19 = BundledFont.entries.dropWhile { it != BundledFont.MONTSERRAT }
        assertEquals(emptyList<BundledFont>(), phase19.filter { it !in used })
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
