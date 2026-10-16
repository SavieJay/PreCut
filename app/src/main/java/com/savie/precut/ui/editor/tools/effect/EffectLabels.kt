package com.savie.precut.ui.editor.tools.effect

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.savie.precut.R
import com.savie.precut.model.effect.EffectCategory
import com.savie.precut.model.effect.EffectType

@StringRes
fun EffectType.label(): Int = when (this) {
    EffectType.BLUR -> R.string.effect_blur
    EffectType.FLASH -> R.string.effect_flash
    EffectType.BW_FLASH -> R.string.effect_bw_flash
    EffectType.VIGNETTE_PULSE -> R.string.effect_vignette_pulse
    EffectType.WOBBLE -> R.string.effect_wobble
    EffectType.EDGE -> R.string.effect_edge
    EffectType.OLD_FILM -> R.string.effect_old_film
    EffectType.FILM_GRAIN -> R.string.effect_film_grain
    EffectType.POSTERIZE -> R.string.effect_posterize
    EffectType.GLITCH -> R.string.effect_glitch
    EffectType.RGB_SPLIT -> R.string.effect_rgb_split
    EffectType.VHS -> R.string.effect_vhs
    EffectType.SHAKE -> R.string.effect_shake
    EffectType.STROBE -> R.string.effect_strobe
    EffectType.PIXELATE -> R.string.effect_pixelate
    EffectType.LIGHT_LEAK -> R.string.effect_light_leak
    EffectType.SOFT_GLOW -> R.string.effect_soft_glow
    EffectType.BOKEH -> R.string.effect_bokeh
    EffectType.SPARKLE -> R.string.effect_sparkle
    EffectType.NEON -> R.string.effect_neon
    EffectType.ZOOM_LENS -> R.string.effect_zoom_lens
    EffectType.FISHEYE -> R.string.effect_fisheye
    EffectType.MIRROR -> R.string.effect_mirror
    EffectType.KALEIDOSCOPE -> R.string.effect_kaleidoscope
    EffectType.SPLIT_2 -> R.string.effect_split_2
    EffectType.SPLIT_3 -> R.string.effect_split_3
    EffectType.SPLIT_4 -> R.string.effect_split_4
    EffectType.SPLIT_9 -> R.string.effect_split_9
}

@DrawableRes
fun EffectType.icon(): Int = when (this) {
    EffectType.BLUR -> R.drawable.ic_blur_on
    EffectType.FLASH -> R.drawable.ic_flash_on
    EffectType.BW_FLASH -> R.drawable.ic_contrast
    EffectType.VIGNETTE_PULSE -> R.drawable.ic_camera
    EffectType.WOBBLE -> R.drawable.ic_waves
    EffectType.EDGE -> R.drawable.ic_deblur
    EffectType.OLD_FILM -> R.drawable.ic_movie
    EffectType.FILM_GRAIN -> R.drawable.ic_grain
    EffectType.POSTERIZE -> R.drawable.ic_filter_vintage
    EffectType.GLITCH -> R.drawable.ic_broken_image
    EffectType.RGB_SPLIT -> R.drawable.ic_gradient
    EffectType.VHS -> R.drawable.ic_tv
    EffectType.SHAKE -> R.drawable.ic_vibration
    EffectType.STROBE -> R.drawable.ic_highlight
    EffectType.PIXELATE -> R.drawable.ic_grid_on
    EffectType.LIGHT_LEAK -> R.drawable.ic_light_mode
    EffectType.SOFT_GLOW -> R.drawable.ic_flare
    EffectType.BOKEH -> R.drawable.ic_lens_blur
    EffectType.SPARKLE -> R.drawable.ic_auto_awesome
    EffectType.NEON -> R.drawable.ic_lightbulb
    EffectType.ZOOM_LENS -> R.drawable.ic_zoom_in
    EffectType.FISHEYE -> R.drawable.ic_bubble_chart
    EffectType.MIRROR -> R.drawable.ic_flip
    EffectType.KALEIDOSCOPE -> R.drawable.ic_cyclone
    EffectType.SPLIT_2 -> R.drawable.ic_splitscreen
    EffectType.SPLIT_3 -> R.drawable.ic_view_agenda
    EffectType.SPLIT_4 -> R.drawable.ic_grid_view
    EffectType.SPLIT_9 -> R.drawable.ic_apps
}

@StringRes
fun EffectCategory.label(): Int = when (this) {
    EffectCategory.BASIC -> R.string.effects_cat_basic
    EffectCategory.RETRO -> R.string.effects_cat_retro
    EffectCategory.GLITCH -> R.string.effects_cat_glitch
    EffectCategory.LIGHT -> R.string.effects_cat_light
    EffectCategory.LENS -> R.string.effects_cat_lens
    EffectCategory.SPLIT -> R.string.effects_cat_split
}
