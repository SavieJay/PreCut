package com.savie.precut.ui.editor.tools

import androidx.annotation.StringRes
import com.savie.precut.R
import com.savie.precut.model.look.Adjustment
import com.savie.precut.model.look.CurveChannel
import com.savie.precut.model.look.FilterCategory
import com.savie.precut.model.look.HslColor

/** Display names of built-in filters, by catalog id. */
private val FILTER_NAMES: Map<String, Int> = mapOf(
    "basic.bright" to R.string.filter_basic_bright,
    "basic.contrast" to R.string.filter_basic_contrast,
    "basic.vivid" to R.string.filter_basic_vivid,
    "basic.mono" to R.string.filter_basic_mono,
    "basic.warm" to R.string.filter_basic_warm,
    "basic.cool" to R.string.filter_basic_cool,
    "basic.vintage" to R.string.filter_basic_vintage,
    "life.fresh" to R.string.filter_life_fresh,
    "life.soft" to R.string.filter_life_soft,
    "life.glow" to R.string.filter_life_glow,
    "life.clear" to R.string.filter_life_clear,
    "life.peach" to R.string.filter_life_peach,
    "life.pastel" to R.string.filter_life_pastel,
    "film.teal_orange" to R.string.filter_film_teal_orange,
    "film.kodak" to R.string.filter_film_kodak,
    "film.fuji" to R.string.filter_film_fuji,
    "film.cinematic" to R.string.filter_film_cinematic,
    "film.moody" to R.string.filter_film_moody,
    "film.bleach" to R.string.filter_film_bleach,
    "film.matte" to R.string.filter_film_matte,
    "retro.70s" to R.string.filter_retro_70s,
    "retro.polaroid" to R.string.filter_retro_polaroid,
    "retro.faded" to R.string.filter_retro_faded,
    "retro.sepia" to R.string.filter_retro_sepia,
    "retro.cross" to R.string.filter_retro_cross,
    "retro.vhs" to R.string.filter_retro_vhs,
    "mono.high_contrast" to R.string.filter_mono_high_contrast,
    "mono.soft" to R.string.filter_mono_soft,
    "mono.silver" to R.string.filter_mono_silver,
    "mono.selenium" to R.string.filter_mono_selenium,
    "mono.noir" to R.string.filter_mono_noir,
    "food.warm_glow" to R.string.filter_food_warm_glow,
    "food.fresh_green" to R.string.filter_food_fresh_green,
    "food.rich" to R.string.filter_food_rich,
    "food.sweet" to R.string.filter_food_sweet,
    "food.crisp" to R.string.filter_food_crisp,
    "nature.lush" to R.string.filter_nature_lush,
    "nature.autumn" to R.string.filter_nature_autumn,
    "nature.ocean" to R.string.filter_nature_ocean,
    "nature.sunset" to R.string.filter_nature_sunset,
    "nature.forest" to R.string.filter_nature_forest,
    "nature.sky" to R.string.filter_nature_sky,
)

@StringRes
fun filterNameRes(filterId: String): Int? = FILTER_NAMES[filterId]

@StringRes
fun FilterCategory.labelRes(): Int = when (this) {
    FilterCategory.BASIC -> R.string.filter_category_basic
    FilterCategory.LIFE -> R.string.filter_category_life
    FilterCategory.FILM -> R.string.filter_category_film
    FilterCategory.RETRO -> R.string.filter_category_retro
    FilterCategory.MONO -> R.string.filter_category_mono
    FilterCategory.FOOD -> R.string.filter_category_food
    FilterCategory.NATURE -> R.string.filter_category_nature
}

@StringRes
fun Adjustment.labelRes(): Int = when (this) {
    Adjustment.BRIGHTNESS -> R.string.adjust_brightness
    Adjustment.CONTRAST -> R.string.adjust_contrast
    Adjustment.SATURATION -> R.string.adjust_saturation
    Adjustment.EXPOSURE -> R.string.adjust_exposure
    Adjustment.SHARPEN -> R.string.adjust_sharpen
    Adjustment.HIGHLIGHTS -> R.string.adjust_highlights
    Adjustment.SHADOWS -> R.string.adjust_shadows
    Adjustment.TEMPERATURE -> R.string.adjust_temperature
    Adjustment.TINT -> R.string.adjust_tint
    Adjustment.HUE -> R.string.adjust_hue
    Adjustment.FADE -> R.string.adjust_fade
    Adjustment.VIGNETTE -> R.string.adjust_vignette
    Adjustment.GRAIN -> R.string.adjust_grain
}

@StringRes
fun HslColor.labelRes(): Int = when (this) {
    HslColor.RED -> R.string.hsl_red
    HslColor.ORANGE -> R.string.hsl_orange
    HslColor.YELLOW -> R.string.hsl_yellow
    HslColor.GREEN -> R.string.hsl_green
    HslColor.CYAN -> R.string.hsl_cyan
    HslColor.BLUE -> R.string.hsl_blue
    HslColor.PURPLE -> R.string.hsl_purple
    HslColor.MAGENTA -> R.string.hsl_magenta
}

@StringRes
fun CurveChannel.labelRes(): Int = when (this) {
    CurveChannel.RGB -> R.string.curves_rgb
    CurveChannel.RED -> R.string.curves_red
    CurveChannel.GREEN -> R.string.curves_green
    CurveChannel.BLUE -> R.string.curves_blue
}
