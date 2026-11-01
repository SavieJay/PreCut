package com.savie.precut.model.ai.edit

import com.savie.precut.model.ClipType
import com.savie.precut.model.MAX_VOLUME
import com.savie.precut.model.edit.EditAction
import com.savie.precut.model.look.Adjustment
import com.savie.precut.model.look.FilterCatalog
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.motion.DEFAULT_ANIMATION_MS
import com.savie.precut.model.motion.DEFAULT_TRANSITION_MS
import com.savie.precut.model.motion.MAX_ANIMATION_MS
import com.savie.precut.model.motion.MAX_TRANSITION_MS
import com.savie.precut.model.motion.MIN_ANIMATION_MS
import com.savie.precut.model.motion.MIN_TRANSITION_MS
import com.savie.precut.model.timelineDurationMs

/** A clip's sound, colour, transition and animations. */

internal fun ToolContext.sound(): Made {
    val (index, clip) = clip()
    if (clip.type == ClipType.IMAGE) fail("Photos have no sound")
    val volume = input.numberOrNull("volume_percent")?.let { inRange("volume_percent", it, 0.0..MAX_VOLUME * 100.0) }
    val muted = input.booleanOrNull("muted")
    val length = seconds(clip.timelineDurationMs())
    val fadeIn = input.numberOrNull("fade_in_seconds")?.let { ms(inRange("fade_in_seconds", it, 0.0..length)) }
    val fadeOut = input.numberOrNull("fade_out_seconds")?.let { ms(inRange("fade_out_seconds", it, 0.0..length)) }
    if (volume != null && clip.keyframes.isNotEmpty()) fail("This clip's volume follows its keyframes, so it can't be set here")
    val actions = listOfNotNull(
        volume?.let { EditAction.SetClipVolume(clip.id, (it / 100).toFloat()) },
        muted?.let { EditAction.SetClipMuted(clip.id, it) },
        if (fadeIn != null || fadeOut != null) EditAction.SetClipFades(clip.id, fadeIn ?: clip.fadeInMs, fadeOut ?: clip.fadeOutMs) else null,
    ).ifEmpty { fail("Nothing to change: every value is null") }
    return Made(EditAction.Batch(actions), AiChangeLabel.Sound(index + 1))
}

internal fun ToolContext.look(): Made {
    val (index, clip) = clip()
    if (clip.keyframes.isNotEmpty()) fail("This clip's colour follows its keyframes, so it can't be set here")
    val filter = input.stringOrNull("filter_id")
    if (filter != null && filter != EditCatalog.NONE && FilterCatalog.presets.none { it.id == filter }) {
        fail("There's no filter $filter; use an id from catalog.filters")
    }
    val strength = input.numberOrNull("filter_strength_percent")?.let { inRange("filter_strength_percent", it, 0.0..100.0) }
    fun adjust(name: String, adjustment: Adjustment, current: Float): Float =
        input.numberOrNull(name)?.let { inRange(name, it, adjustment.min.toDouble()..adjustment.max.toDouble()).toFloat() } ?: current
    val old = clip.look
    val look = old.copy(
        filterId = if (filter == null) old.filterId else filter.takeUnless { it == EditCatalog.NONE },
        filterIntensity = strength?.let { (it / 100).toFloat() } ?: if (filter != null && filter != old.filterId) 1f else old.filterIntensity,
        adjust = old.adjust.copy(
            brightness = adjust("brightness", Adjustment.BRIGHTNESS, old.adjust.brightness),
            contrast = adjust("contrast", Adjustment.CONTRAST, old.adjust.contrast),
            saturation = adjust("saturation", Adjustment.SATURATION, old.adjust.saturation),
            exposure = adjust("exposure", Adjustment.EXPOSURE, old.adjust.exposure),
            temperature = adjust("temperature", Adjustment.TEMPERATURE, old.adjust.temperature),
            vignette = adjust("vignette", Adjustment.VIGNETTE, old.adjust.vignette),
        ),
    )
    return Made(EditAction.SetClipLook(clip.id, look), AiChangeLabel.Look(index + 1))
}

internal fun ToolContext.transition(): Made {
    val (index, clip) = clip("after_clip_id")
    if (index == project.clips.lastIndex) fail("That's the last clip: there's no next clip to go into")
    val name = input.string("transition")
    val seconds = input.numberOrNull("duration_seconds")
        ?.let { inRange("duration_seconds", it, MIN_TRANSITION_MS / 1_000.0..MAX_TRANSITION_MS / 1_000.0) }
    val transition = if (name == EditCatalog.NONE) {
        null
    } else {
        val type = EditCatalog.transition(name) ?: fail("There's no transition $name; use one from catalog.transitions")
        ClipTransition(type, seconds?.let(::ms) ?: clip.transitionOut?.durationMs ?: DEFAULT_TRANSITION_MS)
    }
    return Made(EditAction.SetTransition(clip.id, transition), AiChangeLabel.Transition(index + 1, transition == null), timelineChanged = true)
}

internal fun ToolContext.animation(): Made {
    val (index, clip) = clip()
    fun lengthOf(name: String): Long? = input.numberOrNull(name)
        ?.let { ms(inRange(name, it, MIN_ANIMATION_MS / 1_000.0..MAX_ANIMATION_MS / 1_000.0)) }
    fun animation(name: String, secondsName: String, current: ClipAnimation?): ClipAnimation? {
        val type = input.stringOrNull(name)
        val length = lengthOf(secondsName)
        return when {
            type == EditCatalog.NONE -> null
            type != null -> ClipAnimation(
                EditCatalog.animation(type) ?: fail("There's no animation $type; use one from catalog.animations"),
                length ?: current?.durationMs ?: DEFAULT_ANIMATION_MS,
            )
            length != null -> current?.copy(durationMs = length) ?: fail("$secondsName needs $name: the clip has none")
            else -> current
        }
    }
    val comboName = input.stringOrNull("combo")
    val combo = when (comboName) {
        null -> clip.animationCombo
        EditCatalog.NONE -> null
        else -> ComboAnimation(EditCatalog.combo(comboName) ?: fail("There's no combo $comboName; use one from catalog.combos"))
    }
    val animationIn = animation("in_animation", "in_seconds", clip.animationIn)
    val animationOut = animation("out_animation", "out_seconds", clip.animationOut)
    val newInOut = input.stringOrNull("in_animation").isRealName() || input.stringOrNull("out_animation").isRealName()
    if (comboName.isRealName() && newInOut) fail("A combo replaces in and out animations: set one or the others")
    // In and out replace a combo the clip had, as in the Animation panel.
    val keptCombo = if (newInOut) null else combo
    return Made(EditAction.SetAnimations(clip.id, animationIn, animationOut, keptCombo), AiChangeLabel.Animation(index + 1))
}

private fun String?.isRealName(): Boolean = this != null && this != EditCatalog.NONE
