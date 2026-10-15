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
        val stand = substitutes[clip.uri]?.takeIf { clip.type == ClipType.VIDEO }
        return if (stand == null) clip else clip.copy(uri = stand).also { changed = true }
    }
    val swapped = clips.map(::swap)
    val swappedOverlays = overlays.map { it.copy(clip = swap(it.clip)) }
    return if (changed) copy(clips = swapped, overlays = swappedOverlays) else this
}
