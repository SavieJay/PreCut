package com.savie.precut.ui.editor.tools.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.MAX_TEXT_ANIMATION_MS
import com.savie.precut.model.text.MIN_TEXT_ANIMATION_MS
import com.savie.precut.model.text.TextAnimation
import com.savie.precut.model.text.TextAnimationType
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
import com.savie.precut.ui.components.PresetTile
import com.savie.precut.ui.components.TileGrid
import com.savie.precut.ui.editor.tools.DurationRow

/** Animation (X-04d): In, Out and Loop, each None or one of six, with its duration. */
@Composable
fun TextAnimationTab(overlay: TextOverlay, onChange: (TextOverlay) -> Unit, types: List<TextAnimationType> = TextAnimationType.entries) {
    var slot by rememberSaveable { mutableIntStateOf(0) }
    PillTabs(listOf(R.string.motion_in, R.string.motion_out, R.string.text_loop).map { PillTab(stringResource(it)) }, slot, { slot = it })
    val current = when (slot) {
        0 -> overlay.animationIn
        1 -> overlay.animationOut
        else -> overlay.animationLoop
    }
    fun with(animation: TextAnimation?) = when (slot) {
        0 -> overlay.copy(animationIn = animation)
        1 -> overlay.copy(animationOut = animation)
        else -> overlay.copy(animationLoop = animation)
    }
    TileGrid(listOf<TextAnimationType?>(null) + types, columns = 4) { type ->
        if (type == null) {
            PresetTile(stringResource(R.string.motion_none), R.drawable.ic_block, selected = current == null, onClick = { onChange(with(null)) })
        } else {
            PresetTile(
                stringResource(type.label()), type.icon(), selected = current?.type == type,
                onClick = { onChange(with(TextAnimation(type, current?.durationMs ?: DEFAULT_MS))) },
                badge = R.drawable.ic_play,
            )
        }
    }
    val textMs = overlay.endMs - overlay.startMs
    val top = (if (slot == 2) MAX_TEXT_ANIMATION_MS else minOf(MAX_TEXT_ANIMATION_MS, textMs)).coerceAtLeast(MIN_TEXT_ANIMATION_MS)
    DurationRow(current?.durationMs ?: DEFAULT_MS, MIN_TEXT_ANIMATION_MS, top, enabled = current != null) { ms ->
        current?.let { onChange(with(it.copy(durationMs = ms))) }
    }
}

private const val DEFAULT_MS = 1_200L

private fun TextAnimationType.label(): Int = when (this) {
    TextAnimationType.TYPEWRITER -> R.string.text_anim_typewriter
    TextAnimationType.FADE -> R.string.animation_fade
    TextAnimationType.SLIDE -> R.string.text_anim_slide
    TextAnimationType.POP -> R.string.text_anim_pop
    TextAnimationType.BOUNCE -> R.string.animation_bounce
    TextAnimationType.WAVE -> R.string.text_anim_wave
}

private fun TextAnimationType.icon(): Int = when (this) {
    TextAnimationType.TYPEWRITER -> R.drawable.ic_keyboard
    TextAnimationType.FADE -> R.drawable.ic_gradient
    TextAnimationType.SLIDE -> R.drawable.ic_east
    TextAnimationType.POP -> R.drawable.ic_bubble_chart
    TextAnimationType.BOUNCE -> R.drawable.ic_sports_basketball
    TextAnimationType.WAVE -> R.drawable.ic_waves
}
