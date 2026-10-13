package com.savie.precut.ui.components

import java.util.Locale

/** Parses "#RRGGBB", "RRGGBB" or "#AARRGGBB" into an opaque/ARGB colour int, or null. */
fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) (0xFF000000L or value).toInt() else value.toInt()
}

/** "#RRGGBB" for an ARGB colour (alpha dropped). */
fun formatHexColor(argb: Int): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
