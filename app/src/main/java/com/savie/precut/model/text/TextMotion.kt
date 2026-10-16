package com.savie.precut.model.text

import com.savie.precut.model.AudioClip
import com.savie.precut.model.AudioKind
import com.savie.precut.model.TextOverlay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin

/** An offset (NDC, y up), scale, turn (clockwise) and opacity factor applied on top of a text's place. */
data class TextMotion(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val alpha: Float = 1f,
) {
    operator fun plus(other: TextMotion) =
        TextMotion(dx + other.dx, dy + other.dy, scale * other.scale, rotationDegrees + other.rotationDegrees, alpha * other.alpha)
}

/** The In/Out/Loop animations of a text or sticker (X-04d) at a time into it. */
object TextMotions {

    /** The combined motion [localMs] into a text lasting [totalMs]. */
    fun at(style: TextOverlay, localMs: Long, totalMs: Long): TextMotion {
        var motion = TextMotion()
        style.animationIn?.let { a -> if (localMs < a.durationMs) motion += entrance(a, localMs.toFloat() / a.durationMs) }
        style.animationOut?.let { a ->
            val left = totalMs - localMs
            if (left < a.durationMs) motion += entrance(a, left.toFloat() / a.durationMs)
        }
        style.animationLoop?.let { a -> motion += loop(a, localMs) }
        return motion
    }

    /** Characters a typewriter shows now, of [length]; null when none is running. */
    fun revealAt(style: TextOverlay, length: Int, localMs: Long, totalMs: Long): Int? {
        style.animationIn?.takeIf { it.type == TextAnimationType.TYPEWRITER }?.let { a ->
            if (localMs < a.durationMs) return ceil(length * localMs.toFloat() / a.durationMs).toInt()
        }
        style.animationOut?.takeIf { it.type == TextAnimationType.TYPEWRITER }?.let { a ->
            val left = totalMs - localMs
            if (left < a.durationMs) return ceil(length * left.toFloat() / a.durationMs).toInt()
        }
        style.animationLoop?.takeIf { it.type == TextAnimationType.TYPEWRITER }?.let { a ->
            val phase = (localMs % a.durationMs.coerceAtLeast(1L)).toFloat() / a.durationMs
            return ceil(length * phase).toInt().coerceAtLeast(1)
        }
        return null
    }

    /** An entrance at progress [p] (0 hidden .. 1 in place); exits play it backwards. */
    fun entrance(animation: TextAnimation, p: Float): TextMotion {
        val t = p.coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        return when (animation.type) {
            TextAnimationType.FADE -> TextMotion(alpha = eased)
            TextAnimationType.SLIDE -> TextMotion(dy = -0.25f * (1f - eased), alpha = eased)
            TextAnimationType.POP -> TextMotion(scale = popScale(t), alpha = (t * 3f).coerceAtMost(1f))
            TextAnimationType.BOUNCE -> TextMotion(dy = 0.3f * bounceDrop(t), alpha = (t * 4f).coerceAtMost(1f))
            TextAnimationType.WAVE -> TextMotion(rotationDegrees = 25f * (1f - eased) * sin(t * 4f * PI.toFloat()), alpha = eased)
            TextAnimationType.TYPEWRITER -> TextMotion()
        }
    }

    /** A loop, one cycle per [TextAnimation.durationMs]. */
    fun loop(animation: TextAnimation, localMs: Long): TextMotion {
        val phase = 2f * PI.toFloat() * (localMs % animation.durationMs.coerceAtLeast(1L)) / animation.durationMs
        return when (animation.type) {
            TextAnimationType.FADE -> TextMotion(alpha = 0.7f + 0.3f * sin(phase))
            TextAnimationType.SLIDE -> TextMotion(dx = 0.03f * sin(phase))
            TextAnimationType.POP -> TextMotion(scale = 1f + 0.08f * sin(phase))
            TextAnimationType.BOUNCE -> TextMotion(dy = 0.04f * abs(sin(phase)))
            TextAnimationType.WAVE -> TextMotion(rotationDegrees = 6f * sin(phase), dy = 0.015f * sin(2f * phase))
            TextAnimationType.TYPEWRITER -> TextMotion()
        }
    }

    /** Grows past full size, then settles. */
    private fun popScale(t: Float): Float = when {
        t < 0.6f -> 1.15f * (t / 0.6f)
        else -> 1.15f - 0.15f * ((t - 0.6f) / 0.4f)
    }

    /** Falls in from above and bounces twice: 1 at the top, 0 in place. */
    private fun bounceDrop(t: Float): Float {
        val drop = 1f - t
        return when {
            t < 0.6f -> drop * drop
            t < 0.85f -> 0.12f * sin((t - 0.6f) / 0.25f * PI.toFloat())
            else -> 0.04f * sin((t - 0.85f) / 0.15f * PI.toFloat())
        }
    }
}

/** A text spoken into [uri] ([durationMs] long) as a voiceover clip starting with the text (X-06). */
fun TextOverlay.voiceoverClip(id: String, uri: String, durationMs: Long): AudioClip = AudioClip(
    id = id,
    uri = uri,
    sourceDurationMs = durationMs,
    timelineStartMs = startMs,
    trimEndMs = durationMs,
    kind = AudioKind.VOICEOVER,
)
