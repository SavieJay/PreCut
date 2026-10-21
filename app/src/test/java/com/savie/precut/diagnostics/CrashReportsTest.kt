package com.savie.precut.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportsTest {

    private val about = CrashReports.Context("1.0.0 (19)", "samsung SM-A515F", "13 (API 33)")

    @Test
    fun `names sort by time and are recognised`() {
        val early = CrashReports.fileName(1_700_000_000_000)
        val late = CrashReports.fileName(1_700_000_000_001)
        assertEquals("crash-20231114-221320-000.txt", early)
        assertTrue(early < late)
        assertTrue(CrashReports.isReport(early))
        assertFalse(CrashReports.isReport("notes.txt"))
    }

    @Test
    fun `the report holds when, where, the stack trace and the log`() {
        val error = IllegalStateException("boom", ClassCastException("java.lang.Float cannot be cast to java.lang.Integer"))
        val text = CrashReports.text(1_700_000_000_000, about, "main", error, listOf("W ClipReverser: Reverse failed"))
        assertTrue(text.startsWith("PreCut crash report\nTime: 2023-11-14T22:13:20Z\n"))
        assertTrue("App: 1.0.0 (19)" in text && "Device: samsung SM-A515F" in text && "Android: 13 (API 33)" in text && "Thread: main" in text)
        assertTrue("java.lang.IllegalStateException: boom" in text)
        assertTrue("Caused by: java.lang.ClassCastException" in text)
        assertTrue(text.trimEnd().endsWith("Last log lines:\nW ClipReverser: Reverse failed"))
    }

    @Test
    fun `no log section without log lines`() {
        assertFalse("Last log lines" in CrashReports.text(0, about, "main", RuntimeException("x"), emptyList()))
    }

    @Test
    fun `only the newest five are kept, other files untouched`() {
        val names = (1..8).map { CrashReports.fileName(it * 1_000L) }.shuffled() + "notes.txt"
        val deleted = CrashReports.toDelete(names)
        assertEquals((1..3).map { CrashReports.fileName(it * 1_000L) }, deleted)
        assertEquals(emptyList<String>(), CrashReports.toDelete(names.take(3)))
    }
}
