package com.savie.precut.evals

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.ai.edit.AiChangeLabel
import com.savie.precut.model.ai.edit.MediaFacts
import com.savie.precut.model.ai.edit.Shot
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.text.CaptionInfo
import com.savie.precut.model.text.CaptionWord
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.totalDurationMs
import com.savie.precut.model.videoClip
import kotlin.math.abs

/** What a case looked like after Claude's changes (all kept). */
data class EvalOutcome(val before: Project, val after: Project, val labels: List<AiChangeLabel>, val rejected: Int, val message: String)

/** One eval: a sample project, a request, and what must hold afterwards (empty when it passed). */
data class EvalCase(val id: Int, val prompt: String, val project: Project, val media: MediaFacts?, val check: (EvalOutcome) -> List<String>)

private fun project(vararg lengthsSec: Int, ratio: AspectRatio = AspectRatio.RATIO_9_16) = Project(
    id = "p", name = "Sample", aspectRatio = ratio,
    clips = lengthsSec.mapIndexed { i, s -> videoClip("v${i + 1}", sourceDurationMs = s * 1_000L).copy(sourceWidth = 1920, sourceHeight = 1080) },
    createdAt = 0L, updatedAt = 0L,
)

private fun Project.withMusic(beatEveryMs: Long = 500L) = copy(
    audioClips = audioClips + AudioClip(id = "music", uri = "music", sourceDurationMs = 120_000L, trimEndMs = 120_000L, displayName = "Upbeat track.mp3", kind = AudioKind.MUSIC, beatsMs = (1..240).map { it * beatEveryMs }),
)

/** Captions every 4 s with 3 s of speech and a 1 s pause (and a 3 s pause after 20 s). */
private fun Project.withSpeech(): Project {
    val lines = (0 until totalDurationMs().toInt() / 4_000).map { i ->
        val start = i * 4_000L + if (i >= 5) 2_000L else 0L
        TextOverlay(
            id = "cap$i", text = "line $i of the talk", startMs = start, endMs = start + 3_000L,
            caption = CaptionInfo("g", (0 until 5).map { w -> CaptionWord(listOf("line", "$i", "of", "the", "talk")[w], w * 600L, w * 600L + 450L) }),
        )
    }.filter { it.endMs <= totalDurationMs() }
    return copy(textOverlays = textOverlays + lines)
}

private fun loud(totalSec: Int, quiet: Set<Int> = emptySet()) = MediaFacts(
    loudness = (0 until totalSec).map { if (it in quiet) 0.03f else 0.6f + (it % 5) * 0.08f },
    shots = (0 until totalSec step 5).map { Shot(it * 1_000L, minOf(it + 5, totalSec) * 1_000L, listOf(if (it % 10 == 0) "seashore" else "street sign")) },
)

private fun near(actualMs: Long, targetMs: Long, tolerance: Double = 0.1) = abs(actualMs - targetMs) <= targetMs * tolerance

private fun fail(condition: Boolean, message: String) = if (condition) emptyList() else listOf(message)

/** The 20 evals of docs/AGENT_EVALS.md. */
val EVAL_CASES: List<EvalCase> = listOf(
    EvalCase(1, "Make it 15 seconds", project(10, 10, 10, 10), loud(40)) { o ->
        fail(near(o.after.totalDurationMs(), 15_000L), "length ${o.after.totalDurationMs()} ms, wanted 15 s ±10%")
    },
    EvalCase(2, "Make a 30-second TikTok from these clips, upbeat, cut to the beat", project(12, 12, 12, 12, 12).withMusic(), loud(60)) { o ->
        fail(near(o.after.totalDurationMs(), 30_000L), "length ${o.after.totalDurationMs()} ms, wanted 30 s ±10%") +
            fail(o.labels.any { it is AiChangeLabel.CutToBeats }, "didn't cut to the beat") +
            fail(o.after.aspectRatio == AspectRatio.RATIO_9_16, "not 9:16")
    },
    EvalCase(3, "Remove pauses and add captions", project(20, 20).withSpeech(), loud(40, quiet = (0 until 40).filter { it % 4 == 3 }.toSet())) { o ->
        fail(o.labels.any { it is AiChangeLabel.RemoveTime }, "no pauses removed") + fail(o.labels.any { it is AiChangeLabel.Captions }, "no captions asked for")
    },
    EvalCase(4, "Add a title that says Lagos Trip at the start", project(8, 8), loud(16)) { o ->
        fail(o.after.textOverlays.any { it.text.contains("Lagos Trip", ignoreCase = true) && it.startMs < 1_000L }, "no Lagos Trip title at the start")
    },
    EvalCase(5, "Make the colours warm and bright", project(6, 6, 6), loud(18)) { o ->
        fail(o.after.clips.any { it.look.adjust.temperature > 0f || it.look.adjust.brightness > 0f || it.look.filterId != null }, "no warmer or brighter look")
    },
    EvalCase(6, "Add smooth transitions between all the clips", project(5, 5, 5, 5), loud(20)) { o ->
        fail(o.after.clips.dropLast(1).all { it.transitionOut != null }, "not every junction has a transition")
    },
    EvalCase(7, "Make it square for Instagram", project(6, 6), loud(12)) { o ->
        fail(o.after.aspectRatio == AspectRatio.RATIO_1_1, "not 1:1")
    },
    EvalCase(8, "Slow down the second clip", project(6, 6, 6), loud(18)) { o ->
        fail(o.after.clips.getOrNull(1)?.let { it.speed < 1f } == true, "clip 2 isn't slower")
    },
    EvalCase(9, "Mute the clips' own sound but keep the music", project(6, 6).withMusic(), loud(12)) { o ->
        fail(o.after.clips.all { it.muted || it.volume == 0f } || o.after.originalAudioMuted, "clips still have sound") +
            fail(o.after.audioClips.all { it.volume > 0f }, "music was silenced")
    },
    EvalCase(10, "Delete the last clip", project(5, 6, 7), loud(18)) { o ->
        fail(o.after.clips.map { it.id } == listOf("v1", "v2"), "clips are ${o.after.clips.map { it.id }}")
    },
    EvalCase(11, "Move the last clip to the start", project(5, 6, 7), loud(18)) { o ->
        fail(o.after.clips.firstOrNull()?.id == "v3", "first clip is ${o.after.clips.firstOrNull()?.id}")
    },
    EvalCase(12, "Fade the music out at the end", project(10, 10).withMusic(), loud(20)) { o ->
        fail(o.after.audioClips.any { it.fadeOutMs > 0 }, "no music fade-out")
    },
    EvalCase(13, "Cut the quiet parts", project(30), loud(30, quiet = setOf(4, 5, 6, 15, 16, 17, 18, 25, 26))) { o ->
        fail(o.labels.any { it is AiChangeLabel.RemoveTime } && o.after.totalDurationMs() < 28_000L, "quiet parts not cut")
    },
    EvalCase(14, "Make a calm travel recap with a soft look", project(8, 8, 8, 8), loud(32)) { o ->
        fail(o.after.clips.any { !it.look.isIdentity }, "no look change") + fail(o.after.totalDurationMs() <= o.before.totalDurationMs(), "got longer")
    },
    EvalCase(15, "A highlight reel of the loudest moments, about 20 seconds", project(60), loud(60, quiet = (0 until 60).filter { it % 3 == 0 }.toSet())) { o ->
        fail(near(o.after.totalDurationMs(), 20_000L), "length ${o.after.totalDurationMs()} ms, wanted 20 s ±10%")
    },
    EvalCase(16, "Add a snow effect over the whole video", project(6, 6), loud(12)) { o ->
        val total = o.after.totalDurationMs()
        fail(o.after.effectLayers.any { it.endMs - it.startMs >= total * 0.9 }, "no snow over the whole video")
    },
    EvalCase(17, "Remove the text that says Draft", project(6, 6).copy(textOverlays = listOf(TextOverlay("draft", "Draft", 0L, 3_000L), TextOverlay("keep", "Hello", 4_000L, 6_000L))), loud(12)) { o ->
        fail(o.after.textOverlays.map { it.id } == listOf("keep"), "texts are ${o.after.textOverlays.map { it.id }}")
    },
    EvalCase(18, "Speed up the boring middle clip", project(5, 20, 5), loud(30, quiet = (8..22).toSet())) { o ->
        fail(o.after.clips.getOrNull(1)?.let { it.speed > 1f || it.timelineDurationMs() < 20_000L } == true, "the middle isn't quicker")
    },
    EvalCase(19, "Make it 16:9 for YouTube and add a zoom-in at the start", project(6, 6), loud(12)) { o ->
        fail(o.after.aspectRatio == AspectRatio.RATIO_16_9, "not 16:9") +
            fail(o.after.clips.first().animationIn?.type == AnimationType.ZOOM || o.after.clips.first().keyframes.isNotEmpty(), "no zoom at the start")
    },
    EvalCase(20, "Add my dog photos from last summer", project(6, 6), loud(12)) { o ->
        // It can't reach the gallery: it should say so rather than change things.
        fail(o.labels.isEmpty() && o.message.isNotBlank(), "made changes or said nothing for an impossible request")
    },
)
