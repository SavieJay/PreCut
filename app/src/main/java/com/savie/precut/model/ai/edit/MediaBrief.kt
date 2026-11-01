package com.savie.precut.model.ai.edit

import com.savie.precut.model.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.roundToInt

/** A shot: where the picture stays the same, and what the image model saw in it. */
data class Shot(val startMs: Long, val endMs: Long, val labels: List<String>)

/**
 * What the phone learned from the media for AI Edit (Phase 27), read on the phone and sent as text
 * only: [loudness] for each second of the timeline (0..1, the loudest second is 1) and the [shots].
 */
data class MediaFacts(val loudness: List<Float>, val shots: List<Shot>)

/** Longest lists sent, to keep the brief small (about two minutes of video at full detail). */
private const val MAX_SECONDS = 600
private const val MAX_SHOTS = 60
private const val MAX_LINES = 200

/**
 * The brief's media section: loudness per second (two decimals), shots with their labels, and the
 * captions' lines with their times. Long videos keep the start of each list.
 */
fun Project.mediaBrief(facts: MediaFacts?): JsonObject = buildJsonObject {
    if (facts != null) {
        putJsonArray("loudness_per_second") { facts.loudness.take(MAX_SECONDS).forEach { add(JsonPrimitive((it * 100).roundToInt() / 100.0)) } }
        putJsonArray("shots") {
            facts.shots.take(MAX_SHOTS).forEach { shot ->
                addJsonObject {
                    put("start_seconds", seconds(shot.startMs))
                    put("end_seconds", seconds(shot.endMs))
                    putJsonArray("seen") { shot.labels.forEach { add(JsonPrimitive(it)) } }
                }
            }
        }
    }
    putJsonArray("lines") {
        textOverlays.filter { it.caption != null }.sortedBy { it.startMs }.take(MAX_LINES).forEach { line ->
            addJsonObject {
                put("start_seconds", seconds(line.startMs))
                put("end_seconds", seconds(line.endMs))
                put("text", line.text)
            }
        }
    }
}

/** Shots from picture changes: each runs to the next change (or a clip's edge), at most [MAX_SHOTS]. */
fun shotsFrom(totalMs: Long, cutsMs: List<Long>, clipStartsMs: List<Long>): List<Pair<Long, Long>> {
    val edges = (cutsMs + clipStartsMs + 0L + totalMs).filter { it in 0..totalMs }.distinct().sorted()
    return edges.zipWithNext().filter { (a, b) -> b - a >= 500L }.take(MAX_SHOTS)
}
