package com.savie.precut.media

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import com.savie.precut.model.OutputSize
import javax.inject.Inject

/** Turns pure effect specs into Media3 effects. The only place filter/rotation effects are built. */
@OptIn(UnstableApi::class)
class EffectsFactory @Inject constructor(private val textRenderer: TextOverlayRenderer) {

    /** Colour effects, then user rotation, then fit into the output frame. */
    fun videoEffects(visual: VisualSpec, outputSize: OutputSize): List<Effect> = buildList {
        visual.filter.forEach { add(filterEffect(it)) }
        if (visual.rotationDegrees % 360 != 0) {
            // Media3 rotates counter-clockwise for positive angles; the model is clockwise.
            add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-visual.rotationDegrees.toFloat()).build())
        }
        add(Presentation.createForWidthAndHeight(outputSize.width, outputSize.height, Presentation.LAYOUT_SCALE_TO_FIT))
    }

    /** One overlay effect for all texts, applied to the whole composition in timeline time. */
    fun textOverlayEffect(texts: List<TextSpec>, outputSize: OutputSize): Effect? {
        if (texts.isEmpty()) return null
        return OverlayEffect(texts.map { TimedTextOverlay(it, textRenderer.render(it, outputSize)) })
    }

    private fun filterEffect(effect: FilterEffect): Effect = when (effect) {
        is FilterEffect.Brightness -> Brightness(effect.amount)
        is FilterEffect.Contrast -> Contrast(effect.amount)
        is FilterEffect.Saturation -> HslAdjustment.Builder().adjustSaturation(effect.amount).build()
        FilterEffect.Grayscale -> RgbFilter.createGrayscaleFilter()
        is FilterEffect.RgbScale -> RgbAdjustment.Builder()
            .setRedScale(effect.red)
            .setGreenScale(effect.green)
            .setBlueScale(effect.blue)
            .build()
        is FilterEffect.Matrix -> {
            val values = effect.values.toFloatArray()
            RgbMatrix { _, _ -> values }
        }
    }
}
