package com.savie.precut.model.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * How an animation moves a clip at one instant: multiplies its scale and opacity, adds to its
 * rotation (clockwise degrees) and offset (frame fractions, y down).
 */
data class Pose(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
) {
    operator fun plus(other: Pose) = Pose(
        scale * other.scale,
        offsetX + other.offsetX,
        offsetY + other.offsetY,
        rotationDegrees + other.rotationDegrees,
        opacity * other.opacity,
    )

    companion object {
        val REST = Pose()
    }
}

/**
 * What a transition does to a frame at one instant. [colorMix] blends towards [mixWhite] (white,
 * else black); [flash] adds white; [blur] and [glitch] are 0..1 strengths.
 */
data class TransitionLook(
    val colorMix: Float = 0f,
    val mixWhite: Boolean = false,
    val flash: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val blur: Float = 0f,
    val glitch: Float = 0f,
) {
    companion object {
        val NONE = TransitionLook()
    }
}

/** The animation and transition maths, shared by the GL effects and the unit tests. */
object MotionMath {

    /** One loop of a combo at speed 1. */
    const val COMBO_PERIOD_MS = 1_200L

    /**
     * In/Out durations that fit a clip of [lengthMs]: if both together are longer than the clip,
     * they shrink in proportion.
     */
    fun fittedDurations(inMs: Long, outMs: Long, lengthMs: Long): Pair<Long, Long> {
        val total = inMs + outMs
        if (total <= lengthMs || total == 0L) return inMs to outMs
        val fittedIn = inMs * lengthMs / total
        return fittedIn to lengthMs - fittedIn
    }

    /**
     * The clip's pose [atMs] into its [lengthMs]: a combo loops for the whole clip; otherwise In
     * plays at the start and Out at the end.
     */
    fun pose(
        animationIn: ClipAnimation?,
        animationOut: ClipAnimation?,
        combo: ComboAnimation?,
        atMs: Long,
        lengthMs: Long,
    ): Pose {
        if (combo != null) return comboPose(combo, atMs)
        val (inMs, outMs) = fittedDurations(animationIn?.durationMs ?: 0L, animationOut?.durationMs ?: 0L, lengthMs)
        var pose = Pose.REST
        if (animationIn != null && inMs > 0 && atMs < inMs) {
            pose += enterPose(animationIn.type, atMs.toFloat() / inMs)
        }
        if (animationOut != null && outMs > 0 && atMs > lengthMs - outMs) {
            pose += leavePose(animationOut.type, (lengthMs - atMs).toFloat() / outMs)
        }
        return pose
    }

    /** An In animation at [progress] 0 (start) .. 1 (at rest). */
    fun enterPose(type: AnimationType, progress: Float): Pose {
        val p = progress.coerceIn(0f, 1f)
        val e = easeOutCubic(p)
        val away = 1f - e
        return when (type) {
            AnimationType.FADE -> Pose(opacity = e)
            AnimationType.ZOOM -> Pose(scale = 0.4f + 0.6f * e, opacity = e)
            AnimationType.SLIDE_UP -> Pose(offsetY = away) // rises from below
            AnimationType.SLIDE_DOWN -> Pose(offsetY = -away)
            AnimationType.SLIDE_LEFT -> Pose(offsetX = away) // comes in from the right
            AnimationType.SLIDE_RIGHT -> Pose(offsetX = -away)
            AnimationType.SPIN -> Pose(scale = e, rotationDegrees = -360f * away)
            AnimationType.BOUNCE -> Pose(scale = easeOutBack(p).coerceAtLeast(0f), opacity = min(1f, p * 3f))
            AnimationType.SHAKE -> Pose(offsetX = SHAKE_AMPLITUDE * sin(p * 8f * PI.toFloat()) * (1f - p))
        }
    }

    /** An Out animation at [remaining] 1 (still at rest) .. 0 (gone): the In animation reversed, leaving the other way. */
    fun leavePose(type: AnimationType, remaining: Float): Pose {
        val pose = enterPose(type, remaining)
        return when (type) {
            AnimationType.SLIDE_UP, AnimationType.SLIDE_DOWN, AnimationType.SLIDE_LEFT, AnimationType.SLIDE_RIGHT ->
                pose.copy(offsetX = -pose.offsetX, offsetY = -pose.offsetY)
            AnimationType.SPIN -> pose.copy(rotationDegrees = -pose.rotationDegrees)
            else -> pose
        }
    }

    /** A combo [atMs] into the clip; one loop takes [COMBO_PERIOD_MS] / speed. */
    fun comboPose(combo: ComboAnimation, atMs: Long): Pose {
        val speed = combo.speed.coerceIn(MIN_COMBO_SPEED, MAX_COMBO_SPEED)
        val phase = (atMs * speed / COMBO_PERIOD_MS) % 1f
        val wave = sin(2f * PI.toFloat() * phase)
        return when (combo.type) {
            ComboType.PULSE -> Pose(opacity = 0.75f + 0.25f * cos01(phase))
            ComboType.ZOOM_IN_OUT -> Pose(scale = 1f + 0.12f * wave)
            ComboType.SWING -> Pose(offsetX = 0.06f * wave)
            ComboType.ROCK -> Pose(rotationDegrees = 8f * wave)
            ComboType.SPIN_ZOOM -> Pose(scale = 1f - 0.15f * abs(wave), rotationDegrees = 360f * phase)
            ComboType.BOUNCE -> Pose(offsetY = -0.06f * abs(sin(PI.toFloat() * phase * 2f)))
            ComboType.SHAKE -> Pose(
                offsetX = 0.02f * sin(2f * PI.toFloat() * phase * 6f),
                offsetY = 0.015f * sin(2f * PI.toFloat() * phase * 7f),
            )
        }
    }

    /**
     * Transition durations at the junctions around a clip: each is limited to the shorter of its
     * two clips, so a clip's incoming and outgoing halves never overlap.
     */
    /**
     * How long an overlapping transition really overlaps: its duration, at most half the shorter
     * clip, so a clip's incoming and outgoing overlaps never meet.
     */
    fun fittedOverlapMs(transition: ClipTransition, outgoingLengthMs: Long, incomingLengthMs: Long): Long =
        transition.durationMs.coerceIn(MIN_TRANSITION_MS, MAX_TRANSITION_MS)
            .coerceAtMost(min(outgoingLengthMs, incomingLengthMs) / 2)

    fun fittedTransitionMs(transition: ClipTransition, outgoingLengthMs: Long, incomingLengthMs: Long): Long =
        transition.durationMs.coerceIn(MIN_TRANSITION_MS, MAX_TRANSITION_MS)
            .coerceAtMost(min(outgoingLengthMs, incomingLengthMs))

    /**
     * A transition at [progress] 0 (start of the outgoing half) .. 0.5 (the cut) .. 1 (end of
     * the incoming half). Every effect peaks at the cut.
     */
    fun transitionLook(type: TransitionType, progress: Float): TransitionLook {
        val p = progress.coerceIn(0f, 1f)
        val peak = 1f - abs(2f * p - 1f)
        val eased = easeInOutCubic(peak)
        return when (type) {
            TransitionType.FADE_BLACK -> TransitionLook(colorMix = peak)
            TransitionType.FADE_WHITE -> TransitionLook(colorMix = peak, mixWhite = true)
            TransitionType.FLASH -> TransitionLook(flash = peak.pow(3))
            TransitionType.ZOOM_IN -> TransitionLook(scale = 1f + eased, colorMix = 0.3f * peak.pow(4))
            TransitionType.ZOOM_OUT -> TransitionLook(scale = 1f - 0.6f * eased)
            TransitionType.BLUR -> TransitionLook(blur = eased)
            TransitionType.GLITCH -> TransitionLook(glitch = peak)
            // A full turn: the outgoing clip turns the first half, the incoming one the second.
            TransitionType.SPIN -> TransitionLook(rotationDegrees = 360f * easeInOutCubic(p), scale = 1f - 0.35f * eased)
            // Overlapping ones are drawn by their own layer, not on the clips' edges.
            TransitionType.DISSOLVE, TransitionType.SLIDE, TransitionType.PUSH, TransitionType.WIPE -> TransitionLook()
        }
    }

    /**
     * Where a clip of [lengthMs] is in its transitions [atMs] in: the incoming one ([head], fitted
     * to [headMs]) plays its second half at the start, the outgoing one ([tail], [tailMs]) its
     * first half at the end. Returns the type and progress (see [transitionLook]), or null between them.
     */
    fun transitionAt(head: TransitionType?, headMs: Long, tail: TransitionType?, tailMs: Long, atMs: Long, lengthMs: Long): Pair<TransitionType, Float>? {
        val headHalf = headMs / 2f
        if (head != null && headHalf > 0f && atMs < headHalf) return head to 0.5f + 0.5f * atMs / headHalf
        val tailHalf = tailMs / 2f
        val tailStart = lengthMs - tailHalf
        if (tail != null && tailHalf > 0f && atMs >= tailStart) return tail to (0.5f * (atMs - tailStart) / tailHalf).coerceAtMost(0.5f)
        return null
    }

    fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)

    fun easeInOutCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

    /** Overshoots past 1 before settling (bounce in). */
    fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    private fun cos01(phase: Float): Float = kotlin.math.cos(2f * PI.toFloat() * phase)

    private const val SHAKE_AMPLITUDE = 0.04f
}
