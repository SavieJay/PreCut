package com.savie.precut.model.motion

import kotlin.math.roundToInt

/**
 * Motion blur by frame blending (CapCut Pro): each frame is averaged with the frames just before
 * it, as a camera shutter left open for [shutterMs] would. Pure maths for the GL pass and tests.
 */
object MotionBlur {

    /** The shutter time at full strength. */
    const val MAX_SHUTTER_MS = 100f

    /** Most frames blended at once (current included), to bound GPU memory. */
    const val MAX_FRAMES = 6

    /** Above this many pixels per frame (1080p), fewer frames are kept: each is a full-size texture. */
    const val LARGE_FRAME_PIXELS = 1920L * 1080L
    const val MAX_FRAMES_LARGE = 3

    /** A time step this many frame intervals long (or going backwards) is a seek: start over. */
    const val JUMP_INTERVALS = 2.5f

    fun shutterMs(strength: Float): Float = strength.coerceIn(0f, 1f) * MAX_SHUTTER_MS

    /**
     * How many frames to blend (the current one included) for [strength] when frames arrive every
     * [frameIntervalUs], for frames of [pixels]: the shutter spans that many intervals, so a clip
     * looks the same at 30 and 60 fps.
     */
    fun framesToBlend(strength: Float, frameIntervalUs: Long, pixels: Long): Int {
        if (strength <= 0f || frameIntervalUs <= 0L) return 1
        val cap = if (pixels > LARGE_FRAME_PIXELS) MAX_FRAMES_LARGE else MAX_FRAMES
        val spans = shutterMs(strength) * 1_000f / frameIntervalUs
        return (spans.roundToInt() + 1).coerceIn(1, cap)
    }

    /** Equal weights over the frames actually available (fewer right after a seek or a clip's start). */
    fun weights(frames: Int, available: Int): FloatArray {
        val n = frames.coerceAtMost(available + 1).coerceAtLeast(1)
        return FloatArray(n) { 1f / n }
    }

    /** Whether going from [previousUs] to [nowUs] is a jump (a seek), given frames every [frameIntervalUs]. */
    fun isJump(previousUs: Long, nowUs: Long, frameIntervalUs: Long): Boolean =
        nowUs <= previousUs || (frameIntervalUs > 0 && nowUs - previousUs > frameIntervalUs * JUMP_INTERVALS)
}
