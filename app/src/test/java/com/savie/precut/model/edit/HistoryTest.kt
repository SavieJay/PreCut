package com.savie.precut.model.edit

import com.savie.precut.model.Project
import com.savie.precut.model.project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    private fun named(n: Int): Project = project().copy(name = "v$n")

    @Test
    fun undoAndRedo_walkThroughStates() {
        var h = History(named(0)).push(named(1)).push(named(2))
        h = requireNotNull(h.undo()).also { assertEquals("v1", it.present.name) }
        h = requireNotNull(h.undo()).also { assertEquals("v0", it.present.name) }
        assertNull(h.undo())
        h = requireNotNull(h.redo()).also { assertEquals("v1", it.present.name) }
        assertTrue(h.canRedo)
    }

    @Test
    fun newEdit_dropsRedo() {
        val h = requireNotNull(History(named(0)).push(named(1)).undo()).push(named(9))
        assertFalse(h.canRedo)
        assertEquals(listOf("v0"), h.past.map { it.name })
    }

    @Test
    fun keepsAtMostTheLimit() {
        var h = History(named(0))
        repeat(HISTORY_LIMIT + 10) { h = h.push(named(it + 1)) }
        assertEquals(HISTORY_LIMIT, h.past.size)
        assertEquals("v10", h.past.first().name)
    }

    @Test
    fun sameKeyWithinWindow_coalescesIntoOneStep() {
        val h = History(named(0))
            .push(named(1), coalesceKey = "text:t", nowMs = 1_000L)
            .push(named(2), coalesceKey = "text:t", nowMs = 1_500L)
            .push(named(3), coalesceKey = "text:t", nowMs = 2_400L)
        assertEquals(listOf("v0"), h.past.map { it.name })
        assertEquals("v0", h.undo()?.present?.name)
    }

    @Test
    fun differentKeyOrAfterWindow_startsANewStep() {
        val h = History(named(0))
            .push(named(1), coalesceKey = "text:t", nowMs = 0L)
            .push(named(2), coalesceKey = "text:t", nowMs = COALESCE_WINDOW_MS + 1)
            .push(named(3), coalesceKey = "text:u", nowMs = COALESCE_WINDOW_MS + 2)
        assertEquals(listOf("v0", "v1", "v2"), h.past.map { it.name })
    }

    @Test
    fun onlyTextUpdatesCoalesce() {
        assertEquals("text:t", coalesceKeyFor(EditAction.UpdateText(com.savie.precut.model.TextOverlay("t", "x", 0L, 1L))))
        assertNull(coalesceKeyFor(EditAction.DeleteClip("a")))
    }
}
