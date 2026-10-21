package com.savie.precut.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Keeps a report of each crash in `files/crashes` (the newest [CrashReports.KEEP]) so it can be
 * shared from Settings; nothing is sent anywhere. Native crashes inside codecs end the process
 * before Java sees them and aren't recorded (`adb logcat -b crash` shows those).
 */
object CrashLog {
    private const val DIR = "crashes"
    private const val LOG_LINES = 300
    private const val LOG_TIMEOUT_S = 2L

    /** Saves a report on any uncaught exception, then lets Android handle the crash as before. */
    fun install(context: Context) {
        val app = context.applicationContext
        val about = CrashReports.Context(appVersion(app), "${Build.MANUFACTURER} ${Build.MODEL}", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Whatever goes wrong while saving must not replace the crash being reported.
            runCatching { save(app, about, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Saved reports, newest first. */
    fun reports(context: Context): List<File> =
        dir(context).listFiles().orEmpty().filter { it.isFile && CrashReports.isReport(it.name) }.sortedByDescending { it.name }

    fun deleteAll(context: Context) {
        reports(context).forEach { it.delete() }
    }

    private fun save(context: Context, about: CrashReports.Context, thread: String, error: Throwable) {
        val now = System.currentTimeMillis()
        val dir = dir(context).apply { mkdirs() }
        File(dir, CrashReports.fileName(now)).writeText(CrashReports.text(now, about, thread, error, recentLog()))
        CrashReports.toDelete(dir.list().orEmpty().toList()).forEach { File(dir, it).delete() }
    }

    /** This process's last log lines (an app may read its own log), or none if that fails. */
    private fun recentLog(): List<String> {
        val process = ProcessBuilder("logcat", "-d", "-t", LOG_LINES.toString(), "--pid=${Process.myPid()}").redirectErrorStream(true).start()
        return try {
            val lines = process.inputStream.bufferedReader().readLines()
            process.waitFor(LOG_TIMEOUT_S, TimeUnit.SECONDS)
            lines
        } finally {
            process.destroy()
        }
    }

    private fun dir(context: Context) = File(context.filesDir, DIR)
}

/** "1.0.0 (19)": the app's version name and code. */
fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    "${info.versionName} ($code)"
}.getOrDefault("")
