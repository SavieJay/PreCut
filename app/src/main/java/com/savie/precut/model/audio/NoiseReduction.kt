package com.savie.precut.model.audio

import kotlinx.serialization.Serializable

/**
 * Reduce noise (Phase 22B) on a clip's sound: takes steady background noise (hum, hiss, wind, a
 * fan) out while playing. [profile] is the noise's level per frequency band, measured once from the
 * clip's own sound on the phone; [strength] is how much is taken out. The media are untouched:
 * switching it off plays the original sound.
 */
@Serializable
data class NoiseReduction(
    val profile: List<Float>,
    val strength: Float = DEFAULT_STRENGTH,
) {
    companion object {
        const val DEFAULT_STRENGTH = 0.6f
    }
}

/** Strength within 0..1. */
fun NoiseReduction.clamped(): NoiseReduction = copy(strength = strength.coerceIn(0f, 1f))
