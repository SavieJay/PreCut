package com.savie.precut.model

/**
 * Audio clips stacked into rows so none overlap (CapCut behaviour): each clip, in start order,
 * goes on the first row whose last clip has ended. Deterministic: ties break by id.
 */
fun Project.audioLanes(): List<List<AudioClip>> {
    val lanes = mutableListOf<MutableList<AudioClip>>()
    audioClips.sortedWith(compareBy({ it.timelineStartMs }, { it.id })).forEach { clip ->
        val lane = lanes.firstOrNull { it.last().timelineEndMs() <= clip.timelineStartMs }
        if (lane != null) lane += clip else lanes += mutableListOf(clip)
    }
    return lanes
}
