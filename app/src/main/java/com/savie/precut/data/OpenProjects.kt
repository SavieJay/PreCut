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
    private val flushes = CopyOnWriteArrayList<() -> Unit>()

    /** Adds an open project's [flush]; close the result when the editor closes. */
    fun register(flush: () -> Unit): AutoCloseable {
        flushes += flush
        return AutoCloseable { flushes -= flush }
    }

    /** Writes every open project's unsaved edits now. */
    fun flushAll() = flushes.forEach { it() }
}
