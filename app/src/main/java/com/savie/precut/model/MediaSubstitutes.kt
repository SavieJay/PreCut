package com.savie.precut.model

/**
 * The project with video clips' (and overlays') media swapped per [substitutes] (source Uri → stand-in Uri), e.g.
 * preview proxies. Timing is untouched: a stand-in has the same timestamps as its source. Photos
 * and audio are never swapped. Returns the same instance when nothing changes.
 */
fun Project.withMediaSubstitutes(substitutes: Map<String, String>): Project {
    if (substitutes.isEmpty()) return this
    var changed = false
    fun swap(clip: Clip): Clip {
        val stand = substitutes[clip.uri]?.takeIf { clip.type == ClipType.VIDEO } ?: return clip
        changed = true
        // A background-removal matte still fits the stand-in (same moments), so it follows it.
        val cutout = clip.cutout?.let { if (it.sourceUri == clip.uri) it.copy(sourceUri = stand) else it }
        return clip.copy(uri = stand, cutout = cutout)
    }
    val swapped = clips.map(::swap)
    val swappedOverlays = overlays.map { it.copy(clip = swap(it.clip)) }
    return if (changed) copy(clips = swapped, overlays = swappedOverlays) else this
}
