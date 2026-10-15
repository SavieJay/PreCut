package com.savie.precut.ui.editor.tools

import com.savie.precut.R
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.motion.ComboType
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
