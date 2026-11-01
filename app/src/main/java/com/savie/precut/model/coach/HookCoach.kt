package com.savie.precut.model.coach

import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.edit.TimeRange
import com.savie.precut.model.edit.beatCutPlan
import com.savie.precut.model.edit.spokenWords
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.text.TextTemplates
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.model.totalDurationMs
import kotlin.math.roundToLong

/** Where a hook needs to land: viewers decide in the first few seconds. */
const val HOOK_MS = 3_000L

/** Clips longer than this on average feel slow in a short video. */
const val SLOW_AVERAGE_MS = 4_000L

/** One suggestion of the hook coach (Phase 26B), and the edit its Apply makes. */
sealed interface HookTip {
    /** Nothing written on screen in the first seconds: a title says what's coming. */
    data object AddTitle : HookTip

    /** The first clip runs past the hook; trimmed, the second shot comes sooner. */
    data class TrimFirstClip(val clipId: String, val lengthMs: Long) : HookTip

    /** Speech starts late: the quiet start goes. */
    data class CutQuietStart(val untilMs: Long) : HookTip

    /** The first clip doesn't move: a zoom-in gives the opening energy. */
    data class ZoomInFirstClip(val clipId: String) : HookTip

    /** Slow cuts with music that has beats: cut to the beat. */
    data class CutToBeats(val audioId: String) : HookTip
}

/**
 * The hook coach: looks at the first [HOOK_MS] and at the pacing, and gives up to four tips, most
 * useful first. Rules, not a model: they run on the phone, instantly, for free.
 */
fun Project.hookTips(): List<HookTip> {
    if (clips.isEmpty()) return emptyList()
    val tips = mutableListOf<HookTip>()
    val first = clips.first()
    val words = spokenWords()
    if (textOverlays.none { it.startMs < 2_000L && it.endMs > 300L }) tips += HookTip.AddTitle
    val speechStart = words.firstOrNull()?.startMs
    if (speechStart != null && speechStart > 1_500L) tips += HookTip.CutQuietStart(speechStart - 150L)
    if (first.timelineDurationMs() > HOOK_MS + 1_000L && first.type == ClipType.VIDEO && first.speedCurve == null) {
        tips += HookTip.TrimFirstClip(first.id, HOOK_MS)
    }
    if (first.animationIn == null && first.animationCombo == null && first.keyframes.isEmpty()) tips += HookTip.ZoomInFirstClip(first.id)
    val average = totalDurationMs() / clips.size
    val beats = audioClips.firstOrNull { it.beatsMs.size >= 4 }
    if (beats != null && clips.size >= 2 && average > SLOW_AVERAGE_MS && beatCutPlan(beats.id).cut > 0) tips += HookTip.CutToBeats(beats.id)
    return tips.take(4)
}

/** The edit a tip's Apply makes; [title] is the title's words, [newId] its id. Null when it no longer applies. */
fun Project.hookEdit(tip: HookTip, title: String, newId: String): EditAction? = when (tip) {
    HookTip.AddTitle -> {
        val template = TextTemplates.all.firstOrNull { it.id == "headline" }
        val plain = TextOverlay(id = newId, text = title, startMs = 0L, endMs = minOf(2_500L, totalDurationMs()), yNorm = 0.2f)
        EditAction.AddText(template?.applyTo(plain) ?: plain)
    }
    is HookTip.TrimFirstClip -> clips.firstOrNull { it.id == tip.clipId }?.let { clip ->
        val keep = (tip.lengthMs * clip.speed.toDouble()).roundToLong()
        EditAction.TrimClip(clip.id, clip.trimStartMs, (clip.trimStartMs + keep).coerceAtMost(clip.trimEndMs))
    }
    is HookTip.CutQuietStart -> EditAction.RemoveTimeRanges(listOf(TimeRange(0L, tip.untilMs)))
    is HookTip.ZoomInFirstClip -> clips.firstOrNull { it.id == tip.clipId }?.let {
        EditAction.SetAnimations(it.id, ClipAnimation(AnimationType.ZOOM, 800L), it.animationOut, null)
    }
    is HookTip.CutToBeats -> beatCutPlan(tip.audioId).takeIf { it.cut > 0 }?.edit
}
