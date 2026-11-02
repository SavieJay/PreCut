package com.savie.precut.model.growth

import java.net.URLDecoder

/** Template link ids: 8 letters and digits (the website's `/t/<id>`). */
private val TEMPLATE_ID = Regex("[A-Za-z0-9]{8}")

/**
 * The shared template a Google Play install came for (Phase 28): the website's template pages
 * send people without PreCut to the Play listing with `template=<id>` in Play's install referrer
 * (`utm_source=website&utm_medium=template&utm_campaign=template&template=AbCd1234`). Null for any
 * other referrer, a malformed one, or none.
 */
fun templateFromReferrer(referrer: String?): String? {
    if (referrer.isNullOrBlank()) return null
    val value = referrer.split('&').firstNotNullOfOrNull { pair ->
        val key = pair.substringBefore('=')
        if (key == "template" && '=' in pair) pair.substringAfter('=') else null
    } ?: return null
    val id = try {
        URLDecoder.decode(value, Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        return null
    }
    return id.takeIf { TEMPLATE_ID.matches(it) }
}
