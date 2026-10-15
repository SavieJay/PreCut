package com.savie.precut.ui.editor.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.motion.AnimationType
import com.savie.precut.model.motion.ClipAnimation
import com.savie.precut.model.motion.ComboAnimation
import com.savie.precut.model.motion.ComboType
import com.savie.precut.model.motion.DEFAULT_ANIMATION_MS
import com.savie.precut.model.motion.MAX_ANIMATION_MS
import com.savie.precut.model.motion.MAX_COMBO_SPEED
import com.savie.precut.model.motion.MIN_ANIMATION_MS
import com.savie.precut.model.motion.MIN_COMBO_SPEED
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.PresetTile
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.components.TileGrid
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** A clip's three animation slots, as the Animation panel edits them. */
data class ClipAnimations(val animationIn: ClipAnimation?, val animationOut: ClipAnimation?, val combo: ComboAnimation?)

/**
 * Animation (T-17, T-17b): In, Out and Combo tabs of tiles, then the duration (In/Out, up to
 * [clipLengthMs]) or the loop speed (Combo). A combo replaces In/Out, and picking In/Out clears it.
 */
@Composable
fun AnimationTool(clipId: String, current: ClipAnimations, clipLengthMs: Long, onChange: (Int, ClipAnimations) -> Unit, onDone: () -> Unit) {
    var tab by rememberSaveable(clipId) { mutableIntStateOf(if (current.combo != null) TAB_COMBO else TAB_IN) }
    ToolPanel(stringResource(R.string.tool_animation), onDone) {
        PillTabs(
            listOf(R.string.animation_in, R.string.animation_out, R.string.animation_combo).map { PillTab(stringResource(it)) },
            selected = tab,
            onSelect = { tab = it },
        )
        val maxMs = clipLengthMs.coerceIn(MIN_ANIMATION_MS, MAX_ANIMATION_MS)
        when (tab) {
            TAB_COMBO -> ComboTab(current, onChange)
            else -> {
                val slot = if (tab == TAB_IN) current.animationIn else current.animationOut
                fun with(animation: ClipAnimation?) = if (tab == TAB_IN) {
                    ClipAnimations(animation, current.animationOut, null)
                } else {
                    ClipAnimations(current.animationIn, animation, null)
                }
                TileGrid(listOf<AnimationType?>(null) + AnimationType.entries, columns = 5) { type ->
                    if (type == null) {
                        PresetTile(stringResource(R.string.motion_none), R.drawable.ic_block, selected = slot == null, onClick = { onChange(tab, with(null)) })
                    } else {
                        val (icon, label) = type.tile
                        PresetTile(stringResource(label), icon, selected = slot?.type == type, badge = R.drawable.ic_play, onClick = {
                            onChange(tab, with(ClipAnimation(type, (slot?.durationMs ?: DEFAULT_ANIMATION_MS).coerceAtMost(maxMs))))
                        })
                    }
                }
                DurationRow(slot?.durationMs ?: 0L, MIN_ANIMATION_MS, maxMs, enabled = slot != null && maxMs > MIN_ANIMATION_MS) { ms ->
                    slot?.let { onChange(tab, with(it.copy(durationMs = ms))) }
                }
            }
        }
    }
}

@Composable
private fun ComboTab(current: ClipAnimations, onChange: (Int, ClipAnimations) -> Unit) {
    val combo = current.combo
    TileGrid(listOf<ComboType?>(null) + ComboType.entries, columns = 4) { type ->
        if (type == null) {
            PresetTile(stringResource(R.string.motion_none), R.drawable.ic_block, selected = combo == null, onClick = {
                onChange(TAB_COMBO, ClipAnimations(null, null, null))
            })
        } else {
            val (icon, label) = type.tile
            PresetTile(stringResource(label), icon, selected = combo?.type == type, badge = R.drawable.ic_repeat, onClick = {
                onChange(TAB_COMBO, ClipAnimations(null, null, ComboAnimation(type, combo?.speed ?: 1f)))
            })
        }
    }
    var speed by remember(combo?.speed) { mutableFloatStateOf(combo?.speed ?: 1f) }
    SliderRow(value = stringResource(R.string.motion_speed_value, speed), label = stringResource(R.string.motion_speed)) { modifier ->
        PreCutSlider(
            value = speed,
            onValueChange = { speed = (it * 10f).roundToInt() / 10f },
            onValueChangeFinished = { combo?.let { onChange(TAB_COMBO, ClipAnimations(null, null, it.copy(speed = speed))) } },
            valueRange = MIN_COMBO_SPEED..MAX_COMBO_SPEED,
            anchor = 1f,
            enabled = combo != null,
            modifier = modifier,
        )
    }
    Text(stringResource(R.string.combo_note), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
}

const val TAB_IN = 0
const val TAB_OUT = 1
const val TAB_COMBO = 2
