package com.savie.precut.model.edit

import com.savie.precut.model.Project

/** Most undo steps kept. */
const val HISTORY_LIMIT = 50

/** Edits with the same key within this window merge into one undo step (e.g. typing a text). */
const val COALESCE_WINDOW_MS = 1_000L

/**
 * Immutable undo/redo history of projects. Pure: the caller passes the time, so coalescing is
 * deterministic and testable.
 */
data class History(
    val present: Project,
    val past: List<Project> = emptyList(),
    val future: List<Project> = emptyList(),
    private val lastKey: String? = null,
    private val lastAtMs: Long = Long.MIN_VALUE,
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /**
     * Records [next] as the new present. If [coalesceKey] matches the previous edit's key within
     * [COALESCE_WINDOW_MS], the edits share one undo step. Any redo steps are dropped.
     */
    fun push(next: Project, coalesceKey: String? = null, nowMs: Long = 0L): History {
        val merge = coalesceKey != null && coalesceKey == lastKey && nowMs - lastAtMs <= COALESCE_WINDOW_MS
        return History(
            present = next,
            past = if (merge) past else (past + present).takeLast(HISTORY_LIMIT),
            future = emptyList(),
            lastKey = coalesceKey,
            lastAtMs = nowMs,
        )
    }

    fun undo(): History? = past.lastOrNull()?.let { previous ->
        History(present = previous, past = past.dropLast(1), future = listOf(present) + future)
    }

    fun redo(): History? = future.firstOrNull()?.let { next ->
        History(present = next, past = past + present, future = future.drop(1))
    }
}

/** Which edits merge into one undo step when repeated quickly (null = always a new step). */
fun coalesceKeyFor(action: EditAction): String? = when (action) {
    is EditAction.UpdateText -> "text:${action.overlay.id}"
    else -> null
}
