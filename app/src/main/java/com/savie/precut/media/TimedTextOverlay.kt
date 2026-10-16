package com.savie.precut.media

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.savie.precut.media.sticker.StickerFrames
import com.savie.precut.model.keyframe.valuesAt
import com.savie.precut.model.text.TextMotion
import com.savie.precut.model.text.TextMotions

/**
 * A rendered text or sticker, placed on its anchor (or keyframes), turned, and visible only within its time
 * range, with its In/Out/Loop animations (X-04d). Typewriter frames are rendered on demand by
 * [renderRevealed] (characters shown → bitmap) and kept.
 */
@OptIn(UnstableApi::class)
class TimedTextOverlay(
    private val spec: TextSpec,
    private val bitmap: Bitmap,
    private val renderRevealed: (Int) -> Bitmap = { bitmap },
    /** A GIF sticker's frames, by time since it appeared. */
    private val frames: StickerFrames? = null,
) : BitmapOverlay() {

    private val typewriterFrames = HashMap<Int, Bitmap>()
    private val hidden: OverlaySettings = StaticOverlaySettings.Builder().setAlphaScale(0f).build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        frames?.let { return it.at((presentationTimeUs - spec.startUs) / 1_000) }
        val reveal = revealAt(presentationTimeUs) ?: return bitmap
        if (reveal >= spec.text.length) return bitmap
        return typewriterFrames.getOrPut(reveal) { renderRevealed(reveal) }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        if (!spec.isVisibleAt(presentationTimeUs)) return hidden
        val localMs = (presentationTimeUs - spec.startUs) / 1_000
        val keyed = spec.keyframes.valuesAt(localMs)
        var (x, y) = if (keyed != null) toNdc(keyed.xNorm, keyed.yNorm) else spec.anchorX to spec.anchorY
        var scale = if (keyed != null && spec.textSizeSp > 0f) keyed.textSizeSp / spec.textSizeSp else 1f
        var rotation = keyed?.rotationDegrees ?: spec.rotationDegrees
        var alpha = (keyed?.opacity ?: spec.opacity).coerceIn(0f, 1f)
        val motion = motionAt(presentationTimeUs)
        x += motion.dx
        y += motion.dy
        scale *= motion.scale
        rotation += motion.rotationDegrees
        alpha *= motion.alpha
        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(x, y)
            // Media3 rotates counter-clockwise for positive angles; the model is clockwise.
            .setRotationDegrees(-rotation)
            .setScale(scale, scale)
            .setAlphaScale(alpha.coerceIn(0f, 1f))
            .build()
    }

    /** Characters shown by a typewriter animation now; null when none is running. */
    private fun revealAt(timeUs: Long): Int? =
        TextMotions.revealAt(spec.style, spec.text.length, (timeUs - spec.startUs) / 1_000, (spec.endUs - spec.startUs) / 1_000)

    private fun motionAt(timeUs: Long): TextMotion =
        TextMotions.at(spec.style, (timeUs - spec.startUs) / 1_000, (spec.endUs - spec.startUs) / 1_000)
}
