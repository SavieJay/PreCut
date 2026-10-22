package com.savie.precut.model.motion

import kotlinx.serialization.Serializable

/** The Transition panel's tabs. */
enum class TransitionCategory { BASIC, SLIDE, WIPE, MOTION, GLITCH }

/**
 * Transitions between clips. Edge ones (T-16) play half over each clip with no overlap, so the
 * timeline keeps its length; [overlapping] ones (P-06) show both clips at once, the incoming one
 * starting [ClipTransition.durationMs] earlier, so the video gets shorter by that much. Saved by
 * name, so names never change once shipped.
 */
@Serializable
enum class TransitionType(val category: TransitionCategory, val overlapping: Boolean = false) {
    FADE_BLACK(TransitionCategory.BASIC),
    FADE_WHITE(TransitionCategory.BASIC),
    FLASH(TransitionCategory.BASIC),
    ZOOM_IN(TransitionCategory.MOTION),
    ZOOM_OUT(TransitionCategory.MOTION),
    BLUR(TransitionCategory.BASIC),
    GLITCH(TransitionCategory.GLITCH),
    SPIN(TransitionCategory.MOTION),
    DISSOLVE(TransitionCategory.BASIC, overlapping = true),
    /** In from the right. */
    SLIDE(TransitionCategory.SLIDE, overlapping = true),
    PUSH(TransitionCategory.SLIDE, overlapping = true),
    WIPE(TransitionCategory.WIPE, overlapping = true),

    // Phase 19: edge.
    SHAKE(TransitionCategory.MOTION),
    RGB_SHIFT(TransitionCategory.GLITCH),
    FLIP(TransitionCategory.MOTION),
    STRETCH(TransitionCategory.MOTION),
    STROBE(TransitionCategory.GLITCH),

    // Phase 19: overlapping. Slides and pushes are named by the way the incoming clip moves.
    SLIDE_RIGHT(TransitionCategory.SLIDE, overlapping = true),
    SLIDE_UP(TransitionCategory.SLIDE, overlapping = true),
    SLIDE_DOWN(TransitionCategory.SLIDE, overlapping = true),
    PUSH_RIGHT(TransitionCategory.SLIDE, overlapping = true),
    PUSH_UP(TransitionCategory.SLIDE, overlapping = true),
    PUSH_DOWN(TransitionCategory.SLIDE, overlapping = true),
    CIRCLE(TransitionCategory.WIPE, overlapping = true),
    CLOCK(TransitionCategory.WIPE, overlapping = true),
    BLINDS(TransitionCategory.WIPE, overlapping = true),
    ZOOM_DISSOLVE(TransitionCategory.MOTION, overlapping = true),
    BLUR_DISSOLVE(TransitionCategory.BASIC, overlapping = true),
    PIXELATE(TransitionCategory.GLITCH, overlapping = true),
    INK(TransitionCategory.BASIC, overlapping = true),
}

const val MIN_TRANSITION_MS = 100L
const val MAX_TRANSITION_MS = 2_000L
const val DEFAULT_TRANSITION_MS = 500L

/**
 * A transition into the next clip. The outgoing clip plays the first half over its last
 * [durationMs] / 2, the incoming clip the second half over its first [durationMs] / 2.
 */
@Serializable
data class ClipTransition(val type: TransitionType, val durationMs: Long = DEFAULT_TRANSITION_MS)

/** In/Out animations (T-17). Out plays each one backwards (a slide up leaves upwards). */
@Serializable
enum class AnimationType { FADE, ZOOM, SLIDE_UP, SLIDE_DOWN, SLIDE_LEFT, SLIDE_RIGHT, SPIN, BOUNCE, SHAKE }

const val MIN_ANIMATION_MS = 100L
const val MAX_ANIMATION_MS = 5_000L
const val DEFAULT_ANIMATION_MS = 600L

@Serializable
data class ClipAnimation(val type: AnimationType, val durationMs: Long = DEFAULT_ANIMATION_MS)

/** Looping animations (T-17b): they run for the whole clip and replace In/Out. */
@Serializable
enum class ComboType { PULSE, ZOOM_IN_OUT, SWING, ROCK, SPIN_ZOOM, BOUNCE, SHAKE }

const val MIN_COMBO_SPEED = 0.5f
const val MAX_COMBO_SPEED = 2f

@Serializable
data class ComboAnimation(val type: ComboType, val speed: Float = 1f)
