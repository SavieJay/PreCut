package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * What's typed into a field whose words live in the project. The field shows this, not the
 * project's copy, so keystrokes that arrive before the project's state reaches the screen aren't
 * overwritten; a change from elsewhere (undo, a template) replaces it.
 */
@Stable
class TypedText(initial: String) {
    var value by mutableStateOf(initial)
        private set

    /** Typed values the project hasn't shown back yet. */
    private val pending = mutableListOf<String>()

    fun type(text: String) {
        value = text
        pending += text
    }

    fun sync(stored: String) {
        when {
            stored == value -> pending.clear()
            stored !in pending -> {
                value = stored
                pending.clear()
            }
        }
    }
}

@Composable
fun rememberTypedText(key: Any, stored: String): TypedText {
    val typed = remember(key) { TypedText(stored) }
    SideEffect { typed.sync(stored) }
    return typed
}
