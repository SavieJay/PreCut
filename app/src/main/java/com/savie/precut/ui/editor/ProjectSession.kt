package com.savie.precut.ui.editor

import com.savie.precut.data.ProjectRepository
import com.savie.precut.model.Project
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.EditReducer
import com.savie.precut.model.edit.History
import com.savie.precut.model.edit.coalesceKeyFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * One open project: applies edits through the reducer, keeps undo/redo history and auto-saves
 * (debounced). Saves run in [appScope] so the last one completes even after the editor closes.
 */
@OptIn(FlowPreview::class)
class ProjectSession(
    initial: Project,
    private val repository: ProjectRepository,
    private val clock: Clock,
    private val appScope: CoroutineScope,
    screenScope: CoroutineScope,
) {
    var history = History(initial)
        private set

    val project: Project get() = history.present

    private val pendingSave = MutableStateFlow<Project?>(null)

    init {
        screenScope.launch {
            pendingSave.filterNotNull().debounce(AUTOSAVE_DEBOUNCE_MS).collect { save(it) }
        }
    }

    /** The edited project, or null when [action] changed nothing. */
    fun apply(action: EditAction): Project? {
        val edited = EditReducer.reduce(history.present, action)
        if (edited === history.present) return null
        val now = clock.millis()
        val stamped = edited.copy(updatedAt = now)
        history = history.push(stamped, coalesceKeyFor(action), now)
        pendingSave.value = stamped
        return stamped
    }

    fun undo(): Project? = history.undo()?.let(::restore)

    fun redo(): Project? = history.redo()?.let(::restore)

    /** Saves now (e.g. when leaving the screen) instead of waiting for the debounce. */
    fun flush() {
        val project = pendingSave.value ?: return
        pendingSave.value = null
        save(project)
    }

    private fun restore(next: History): Project {
        history = next
        // Undo is itself an edit as far as "last edited" goes.
        val stamped = next.present.copy(updatedAt = clock.millis())
        pendingSave.value = stamped
        return stamped
    }

    private fun save(project: Project) {
        appScope.launch { repository.save(project) }
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 500L
    }
}
