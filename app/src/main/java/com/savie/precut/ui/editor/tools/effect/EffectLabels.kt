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
    EffectType.HEARTBEAT -> R.string.effect_heartbeat
    EffectType.CONFETTI -> R.string.effect_confetti
    EffectType.DISCO -> R.string.effect_disco
    EffectType.RAINBOW -> R.string.effect_rainbow
    EffectType.INVERT_FLASH -> R.string.effect_invert_flash
    EffectType.LENS_FLARE -> R.string.effect_lens_flare
    EffectType.DREAMY_GLOW -> R.string.effect_dreamy_glow
    EffectType.SPOTLIGHT -> R.string.effect_spotlight
    EffectType.CRT -> R.string.effect_crt
    EffectType.TV_STATIC -> R.string.effect_tv_static
    EffectType.CHROMATIC_PULSE -> R.string.effect_chromatic_pulse
    EffectType.DUOTONE -> R.string.effect_duotone
    EffectType.HALFTONE -> R.string.effect_halftone
    EffectType.SKETCH -> R.string.effect_sketch
    EffectType.THERMAL -> R.string.effect_thermal
    EffectType.NIGHT_VISION -> R.string.effect_night_vision
    EffectType.SNOW -> R.string.effect_snow
    EffectType.RAIN -> R.string.effect_rain
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
    EffectType.HEARTBEAT -> R.drawable.ic_favorite
    EffectType.CONFETTI -> R.drawable.ic_fx_confetti
    EffectType.DISCO -> R.drawable.ic_fx_disco
    EffectType.RAINBOW -> R.drawable.ic_fx_rainbow
    EffectType.INVERT_FLASH -> R.drawable.ic_invert
    EffectType.LENS_FLARE -> R.drawable.ic_fx_lens_flare
    EffectType.DREAMY_GLOW -> R.drawable.ic_auto_fix_high
    EffectType.SPOTLIGHT -> R.drawable.ic_fx_spotlight
    EffectType.CRT -> R.drawable.ic_fx_crt
    EffectType.TV_STATIC -> R.drawable.ic_grain
    EffectType.CHROMATIC_PULSE -> R.drawable.ic_fx_rgb
    EffectType.DUOTONE -> R.drawable.ic_palette
    EffectType.HALFTONE -> R.drawable.ic_fx_halftone
    EffectType.SKETCH -> R.drawable.ic_draw
    EffectType.THERMAL -> R.drawable.ic_fx_thermal
    EffectType.NIGHT_VISION -> R.drawable.ic_fx_night_vision
    EffectType.SNOW -> R.drawable.ic_fx_snow
    EffectType.RAIN -> R.drawable.ic_fx_rain
}

@StringRes
fun EffectCategory.label(): Int = when (this) {
    EffectCategory.BASIC -> R.string.effects_cat_basic
    EffectCategory.PARTY -> R.string.effects_cat_party
    EffectCategory.RETRO -> R.string.effects_cat_retro
    EffectCategory.GLITCH -> R.string.effects_cat_glitch
    EffectCategory.ART -> R.string.effects_cat_art
    EffectCategory.WEATHER -> R.string.effects_cat_weather
    EffectCategory.LIGHT -> R.string.effects_cat_light
    EffectCategory.LENS -> R.string.effects_cat_lens
    EffectCategory.SPLIT -> R.string.effects_cat_split
}
