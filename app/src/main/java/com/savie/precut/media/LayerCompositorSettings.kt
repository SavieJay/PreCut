package com.savie.precut.media

import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import com.savie.precut.model.OutputSize

/**
 * Stacks full-frame layers (each input is already placed on an output-size frame). An input with
 * [visibleUs] ranges shows only inside them: outside, Media3 fills a sequence's gaps with opaque
 * black frames, which would cover everything below. A null entry is always visible.
 */
@OptIn(UnstableApi::class)
class LayerCompositorSettings(
    private val outputSize: OutputSize,
    private val visibleUs: List<List<LongRange>?>,
) : VideoCompositorSettings {

    override fun getOutputSize(inputSizes: List<Size>): Size = Size(outputSize.width, outputSize.height)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings =
        if (isVisible(inputId, presentationTimeUs)) VISIBLE else HIDDEN

    /** Whether input [inputId] shows at composition time [presentationTimeUs]. */
    fun isVisible(inputId: Int, presentationTimeUs: Long): Boolean {
        val ranges = visibleUs.getOrNull(inputId) ?: return true
        return ranges.any { presentationTimeUs in it }
    }

    private companion object {
        // Lazy: built on first use (on the GL thread), so the visibility logic is testable on the JVM.
        val VISIBLE: OverlaySettings by lazy { StaticOverlaySettings.Builder().build() }
        val HIDDEN: OverlaySettings by lazy { StaticOverlaySettings.Builder().setAlphaScale(0f).build() }
    }
}
