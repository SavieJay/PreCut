package com.savie.precut.model.motion

import kotlinx.serialization.Serializable

/**
 * Transitions between clips. Edge ones (T-16) play half over each clip with no overlap, so the
 * timeline keeps its length; [overlapping] ones (P-06) show both clips at once, the incoming one
 * starting [ClipTransition.durationMs] earlier, so the video gets shorter by that much.
 */
@Serializable
enum class TransitionType(val overlapping: Boolean = false) {
    FADE_BLACK, FADE_WHITE, FLASH, ZOOM_IN, ZOOM_OUT, BLUR, GLITCH, SPIN,
    DISSOLVE(overlapping = true), SLIDE(overlapping = true), PUSH(overlapping = true), WIPE(overlapping = true),
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
