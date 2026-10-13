package com.savie.precut.model

import com.savie.precut.model.look.LookLayer

/**
 * Items stacked into rows so none overlap (CapCut behaviour): each item, in start order, goes on
 * the first row whose last item has ended. Deterministic: ties break by [id].
 */
fun <T> stackIntoLanes(items: List<T>, start: (T) -> Long, end: (T) -> Long, id: (T) -> String): List<List<T>> {
    val lanes = mutableListOf<MutableList<T>>()
    items.sortedWith(compareBy(start, id)).forEach { item ->
        val lane = lanes.firstOrNull { end(it.last()) <= start(item) }
        if (lane != null) lane += item else lanes += mutableListOf(item)
    }
    return lanes
}

/** Audio clips stacked into rows. */
fun Project.audioLanes(): List<List<AudioClip>> =
    stackIntoLanes(audioClips, AudioClip::timelineStartMs, AudioClip::timelineEndMs, AudioClip::id)

/** Text overlays stacked into rows. */
fun Project.textLanes(): List<List<TextOverlay>> =
    stackIntoLanes(textOverlays, TextOverlay::startMs, TextOverlay::endMs, TextOverlay::id)

/** Look layers stacked into rows. */
fun Project.lookLayerLanes(): List<List<LookLayer>> =
    stackIntoLanes(lookLayers, LookLayer::startMs, LookLayer::endMs, LookLayer::id)
