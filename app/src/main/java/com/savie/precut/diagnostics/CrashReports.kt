package com.savie.precut.diagnostics

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Crash reports' text and file names (pure, for unit tests). A report is plain text: when and on
 * what it happened, the stack trace, and PreCut's own last log lines. Names sort by time, and only
 * the newest [KEEP] are kept.
 */
object CrashReports {
    const val KEEP = 5
    private const val PREFIX = "crash-"
    private const val SUFFIX = ".txt"
    private val NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)

    /** The device and app a report comes from. */
    data class Context(val appVersion: String, val device: String, val android: String)

    fun fileName(timeMs: Long): String = PREFIX + NAME_TIME.format(Instant.ofEpochMilli(timeMs)) + SUFFIX

    fun isReport(name: String): Boolean = name.startsWith(PREFIX) && name.endsWith(SUFFIX)

    /** The report's text for [error] on [thread] at [timeMs]. */
    fun text(timeMs: Long, context: Context, thread: String, error: Throwable, log: List<String>): String = buildString {
        appendLine("PreCut crash report")
        appendLine("Time: ${DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timeMs))}")
        appendLine("App: ${context.appVersion}")
        appendLine("Device: ${context.device}")
        appendLine("Android: ${context.android}")
        appendLine("Thread: $thread")
        appendLine()
        appendLine(error.stackTraceToString().trimEnd())
        if (log.isNotEmpty()) {
            appendLine()
            appendLine("Last log lines:")
            log.forEach(::appendLine)
        }
    }

    /** Of [names], the reports beyond the newest [keep] (oldest first). */
    fun toDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter(::isReport).sorted().dropLast(keep)
}
