package com.savie.precut.ui.components

import java.util.Locale

/** Formats a duration as `m:ss`, or `h:mm:ss` from one hour. Negative values count as zero. */
fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** Formats a time as `m:ss.t` (tenths), or `h:mm:ss.t` from one hour; for precise drag feedback. */
fun formatPreciseMs(timeMs: Long): String {
    val ms = timeMs.coerceAtLeast(0L)
    val tenths = (ms % 1000) / 100
    return formatDurationMs(ms) + "." + tenths
}
