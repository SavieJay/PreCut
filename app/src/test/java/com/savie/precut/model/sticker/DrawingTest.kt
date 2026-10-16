package com.savie.precut.model.sticker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingTest {
    private val line = listOf(0.1f to 0.1f, 0.5f to 0.5f)

    @Test
    fun strokes_takeTheBrush() {
        val d = Drawing(colorArgb = 0xFF00FF00.toInt(), size = 36f).withStroke(line)
        assertEquals(DrawStroke(line, 0xFF00FF00.toInt(), 0.1f, eraser = false), d.strokes.single())
    }

    @Test
    fun undoAndRedo_walkTheStrokes() {
        val d = Drawing().withStroke(line).withStroke(listOf(0.2f to 0.2f))
        val undone = d.undo()
        assertEquals(1, undone.strokes.size)
        assertTrue(undone.canRedo)
        assertEquals(d.strokes, undone.redo().strokes)
        // A new stroke after an undo drops what was undone.
        assertFalse(undone.withStroke(line).canRedo)
        assertEquals(Drawing(), Drawing().undo())
    }

    @Test
    fun eraserOnly_isBlank() {
        assertTrue(Drawing().isBlank)
        assertTrue(Drawing(eraser = true).withStroke(line).isBlank)
        assertFalse(Drawing().withStroke(line).isBlank)
        assertEquals(Drawing(), Drawing().withStroke(emptyList()))
    }
}
