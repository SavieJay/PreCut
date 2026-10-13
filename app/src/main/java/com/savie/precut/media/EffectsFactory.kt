package com.savie.precut.media

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import com.savie.precut.media.look.LookEffect
import com.savie.precut.media.look.LutCache
import com.savie.precut.model.OutputSize
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** Turns pure effect specs into Media3 effects. The only place filter/rotation effects are built. */
@OptIn(UnstableApi::class)
class EffectsFactory @Inject constructor(
    private val textRenderer: TextOverlayRenderer,
    private val thumbnails: ThumbnailLoader,
    private val luts: LutCache,
) {

    /** Grading (at source resolution), then user rotation, then fit into the output frame. */
    fun videoEffects(visual: VisualSpec, outputSize: OutputSize): List<Effect> = buildList {
        visual.look?.let { add(LookEffect(it, luts)) }
        if (visual.rotationDegrees % 360 != 0) {
            // Media3 rotates counter-clockwise for positive angles; the model is clockwise.
            add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-visual.rotationDegrees.toFloat()).build())
        }
        add(Presentation.createForWidthAndHeight(outputSize.width, outputSize.height, Presentation.LAYOUT_SCALE_TO_FIT))
    }

    /**
     * Volume, then fades, then pitch; each stage only when it does something. Volume and fades
     * are separate processors because a fade's shape replaces (not scales) the provider's gain.
     */
    fun audioProcessors(sound: SoundSpec): List<AudioProcessor> = buildList {
        if (sound.volume != 1f) add(GainProcessor(DefaultGainProvider.Builder(sound.volume).build()))
        if (sound.fadeInMs > 1 || sound.fadeOutMs > 1) {
            val fades = DefaultGainProvider.Builder(/* defaultGain= */ 1f)
            if (sound.fadeInMs > 1) fades.addFadeAt(0L, sound.fadeInMs * 1_000, DefaultGainProvider.FADE_IN_LINEAR)
            if (sound.fadeOutMs > 1) {
                val start = (sound.lengthMs - sound.fadeOutMs).coerceAtLeast(0L)
                fades.addFadeAt(start * 1_000, sound.fadeOutMs * 1_000, DefaultGainProvider.FADE_OUT_LINEAR)
            }
            add(GainProcessor(fades.build()))
        }
        if (sound.pitch != 1f) add(SonicAudioProcessor().apply { setPitch(sound.pitch) })
    }

    /**
     * One overlay effect for the cover and all texts, applied to the whole composition in
     * timeline time. The cover is drawn first so texts stay on top.
     */
    fun overlayEffect(texts: List<TextSpec>, cover: CoverSpec?, outputSize: OutputSize): Effect? {
        if (texts.isEmpty() && cover == null) return null
        val coverOverlay = cover?.let { spec ->
            val source = spec.source
            // Runs once on the effects thread; the loader's own IO work happens on its dispatcher.
            CoverOverlay(spec, outputSize) {
                runBlocking {
                    val key = ThumbnailKey(source.uri, source.type, source.sourceMs, outputSize.height, source.rotationDegrees, exact = true)
                    thumbnails.load(key, cache = false)
                }
            }
        }
        return OverlayEffect(listOfNotNull(coverOverlay) + texts.map { TimedTextOverlay(it, textRenderer.render(it, outputSize)) })
    }

    /** Look layers, each active only in its time range (composition time). */
    fun lookLayerEffects(layers: List<LookLayerSpec>): List<Effect> =
        layers.map { LookEffect(it.look, luts, it.startUs until it.endUs) }
}
