package com.savie.precut.data

import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The projects open in an editor, so their pending autosave can be written at once from outside
 * (before the forced-update screen, Phase 23).
 */
@Singleton
class OpenProjects @Inject constructor() {
    private class Open(val projectId: String, val flush: () -> Unit)

    private val open = CopyOnWriteArrayList<Open>()

    /** Adds open project [projectId] and its [flush]; close the result when the editor closes. */
    fun register(projectId: String, flush: () -> Unit): AutoCloseable {
        val entry = Open(projectId, flush)
        open += entry
        return AutoCloseable { open -= entry }
    }

    /** Writes every open project's unsaved edits now. */
    fun flushAll() = open.forEach { it.flush() }

    /** Whether an editor has [projectId] open (cloud sync leaves it alone until it closes, Phase 30D). */
    fun isOpen(projectId: String): Boolean = open.any { it.projectId == projectId }
}
