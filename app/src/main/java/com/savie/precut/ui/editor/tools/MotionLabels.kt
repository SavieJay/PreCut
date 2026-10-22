package com.savie.precut.ui.editor.tools

import com.savie.precut.R
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.motion.ComboType
import com.savie.precut.model.motion.TransitionCategory
import com.savie.precut.model.motion.TransitionType

/** Tile icon and label for each transition (T-16). */
val TransitionType.tile: Pair<Int, Int>
    get() = when (this) {
        TransitionType.FADE_BLACK -> R.drawable.ic_tr_fade_black to R.string.transition_fade_black
        TransitionType.FADE_WHITE -> R.drawable.ic_tr_fade_white to R.string.transition_fade_white
        TransitionType.FLASH -> R.drawable.ic_tr_flash to R.string.transition_flash
        TransitionType.ZOOM_IN -> R.drawable.ic_tr_zoom_in to R.string.transition_zoom_in
        TransitionType.ZOOM_OUT -> R.drawable.ic_tr_zoom_out to R.string.transition_zoom_out
        TransitionType.BLUR -> R.drawable.ic_tr_blur to R.string.transition_blur
        TransitionType.GLITCH -> R.drawable.ic_tr_glitch to R.string.transition_glitch
        TransitionType.SPIN -> R.drawable.ic_spin to R.string.transition_spin
        TransitionType.DISSOLVE -> R.drawable.ic_blur_circular to R.string.transition_dissolve
        TransitionType.SLIDE -> R.drawable.ic_tr_slide_left to R.string.transition_slide
        TransitionType.PUSH -> R.drawable.ic_tr_push_left to R.string.transition_push
        TransitionType.WIPE -> R.drawable.ic_swipe_right to R.string.transition_wipe
        TransitionType.SHAKE -> R.drawable.ic_vibration to R.string.transition_shake
        TransitionType.RGB_SHIFT -> R.drawable.ic_fx_rgb to R.string.transition_rgb_shift
        TransitionType.FLIP -> R.drawable.ic_flip to R.string.transition_flip
        TransitionType.STRETCH -> R.drawable.ic_tr_stretch to R.string.transition_stretch
        TransitionType.STROBE -> R.drawable.ic_bolt to R.string.transition_strobe
        TransitionType.SLIDE_RIGHT -> R.drawable.ic_tr_slide_right to R.string.transition_slide_right
        TransitionType.SLIDE_UP -> R.drawable.ic_tr_slide_up to R.string.transition_slide_up
        TransitionType.SLIDE_DOWN -> R.drawable.ic_tr_slide_down to R.string.transition_slide_down
        TransitionType.PUSH_RIGHT -> R.drawable.ic_tr_push_right to R.string.transition_push_right
        TransitionType.PUSH_UP -> R.drawable.ic_tr_push_up to R.string.transition_push_up
        TransitionType.PUSH_DOWN -> R.drawable.ic_tr_push_down to R.string.transition_push_down
        TransitionType.CIRCLE -> R.drawable.ic_tr_circle to R.string.transition_circle
        TransitionType.CLOCK -> R.drawable.ic_tr_clock to R.string.transition_clock
        TransitionType.BLINDS -> R.drawable.ic_tr_blinds to R.string.transition_blinds
        TransitionType.ZOOM_DISSOLVE -> R.drawable.ic_open_in_full to R.string.transition_zoom_dissolve
        TransitionType.BLUR_DISSOLVE -> R.drawable.ic_tr_blur_dissolve to R.string.transition_blur_dissolve
        TransitionType.PIXELATE -> R.drawable.ic_grid_on to R.string.transition_pixelate
        TransitionType.INK -> R.drawable.ic_tr_ink to R.string.transition_ink
    }

/** Tile icon and label for each In/Out animation (T-17). */
val AnimationType.tile: Pair<Int, Int>
    get() = when (this) {
        AnimationType.FADE -> R.drawable.ic_anim_fade to R.string.animation_fade
        AnimationType.ZOOM -> R.drawable.ic_anim_zoom to R.string.animation_zoom
        AnimationType.SLIDE_UP -> R.drawable.ic_anim_up to R.string.animation_slide_up
        AnimationType.SLIDE_DOWN -> R.drawable.ic_anim_down to R.string.animation_slide_down
        AnimationType.SLIDE_LEFT -> R.drawable.ic_anim_left to R.string.animation_slide_left
        AnimationType.SLIDE_RIGHT -> R.drawable.ic_anim_right to R.string.animation_slide_right
        AnimationType.SPIN -> R.drawable.ic_spin to R.string.animation_spin
        AnimationType.BOUNCE -> R.drawable.ic_anim_bounce to R.string.animation_bounce
        AnimationType.SHAKE -> R.drawable.ic_anim_shake to R.string.animation_shake
    }

/** Tile icon and label for each combo (T-17b). */
val ComboType.tile: Pair<Int, Int>
    get() = when (this) {
        ComboType.PULSE -> R.drawable.ic_anim_fade to R.string.combo_pulse
        ComboType.ZOOM_IN_OUT -> R.drawable.ic_anim_zoom to R.string.combo_zoom
        ComboType.SWING -> R.drawable.ic_replace to R.string.combo_swing
        ComboType.ROCK -> R.drawable.ic_anim_shake to R.string.combo_rock
        ComboType.SPIN_ZOOM -> R.drawable.ic_spin to R.string.combo_spin
        ComboType.BOUNCE -> R.drawable.ic_anim_bounce to R.string.combo_bounce
        ComboType.SHAKE -> R.drawable.ic_anim_waves to R.string.combo_shake
    }

/** Tab label for each group of transitions. */
val TransitionCategory.label: Int
    get() = when (this) {
        TransitionCategory.BASIC -> R.string.transition_cat_basic
        TransitionCategory.SLIDE -> R.string.transition_cat_slide
        TransitionCategory.WIPE -> R.string.transition_cat_wipe
        TransitionCategory.MOTION -> R.string.transition_cat_motion
        TransitionCategory.GLITCH -> R.string.transition_cat_glitch
    }
