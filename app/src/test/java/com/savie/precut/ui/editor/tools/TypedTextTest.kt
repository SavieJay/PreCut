package com.savie.precut.ui.editor.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class TypedTextTest {
    @Test
    fun keystrokes_surviveALaggingProject() {
        val typed = TypedText("")
        typed.type("H")
        typed.type("He")
        typed.type("Hel")
        typed.sync("H") // The screen still shows an older project.
        assertEquals("Hel", typed.value)
        typed.sync("Hel")
        assertEquals("Hel", typed.value)
    }

    @Test
    fun undo_replacesWhatWasTyped() {
        val typed = TypedText("Hi")
        typed.type("Hi!")
        typed.sync("Hi!")
        typed.sync("Hi") // Undo.
        assertEquals("Hi", typed.value)
    }

    @Test
    fun template_replacesWhatWasTyped() {
        val typed = TypedText("")
        typed.type("a")
        typed.sync("SALE")
        assertEquals("SALE", typed.value)
    }
}
