package com.savie.precut.ui.editor

import com.savie.precut.data.InMemoryProjectRepository
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.project
import com.savie.precut.model.videoClip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSessionTest {

    private val repository = InMemoryProjectRepository()
    private val clock = Clock.fixed(Instant.ofEpochMilli(99L), ZoneOffset.UTC)

    private fun TestScope.session() =
        ProjectSession(project(videoClip("a"), videoClip("b")), repository, clock, appScope = this, screenScope = backgroundScope)

    @Test
    fun edits_areSavedAfterTheDebounce() = runTest {
        val session = session()
        runCurrent()
        session.apply(EditAction.DeleteClip("b"))
        advanceTimeBy(400)
        runCurrent()
        assertNull(repository.load("p"))
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("a"), repository.load("p")?.clips?.map { it.id })
        assertEquals(99L, repository.load("p")?.updatedAt)
    }

    @Test
    fun flush_savesImmediately() = runTest {
        val session = session()
        runCurrent()
        session.apply(EditAction.DeleteClip("b"))
        session.flush()
        runCurrent()
        assertEquals(1, repository.load("p")?.clips?.size)
    }

    @Test
    fun undoRedo_restoreAndAreSaved() = runTest {
        val session = session()
        runCurrent()
        session.apply(EditAction.DeleteClip("b"))
        assertEquals(2, session.undo()?.clips?.size)
        session.flush()
        runCurrent()
        assertEquals(2, repository.load("p")?.clips?.size)
        assertEquals(1, session.redo()?.clips?.size)
    }

    @Test
    fun noOpEdits_returnNull_andAddNoHistory() = runTest {
        val session = session()
        assertNull(session.apply(EditAction.DeleteClip("missing")))
        assertNull(session.undo())
    }
}
