package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.motion.ClipTransition
import com.savie.precut.model.motion.MAX_TRANSITION_MS
import com.savie.precut.model.motion.MIN_TRANSITION_MS
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.ui.components.PresetTile
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.components.TileGrid
import kotlin.math.roundToLong

/** What the Transition panel can do. */
interface TransitionActions {
    fun onType(type: TransitionType?)
    fun onDuration(durationMs: Long)
    fun onApplyAll()
}

/**
 * Transition (T-16): None and the edge transitions in a 5-column grid, then the duration, up to
 * [maxMs] (the shorter of the two clips). Picking one plays it once.
 */
@Composable
fun TransitionTool(transition: ClipTransition?, maxMs: Long, actions: TransitionActions, onDone: () -> Unit) {
    ToolPanel(stringResource(R.string.tool_transition), onDone, actions = { ApplyToAllButton(actions::onApplyAll) }) {
        TileGrid(listOf<TransitionType?>(null) + TransitionType.entries, columns = 5) { type ->
            if (type == null) {
                PresetTile(stringResource(R.string.motion_none), R.drawable.ic_block, selected = transition == null, onClick = { actions.onType(null) })
            } else {
                val (icon, label) = type.tile
                PresetTile(stringResource(label), icon, selected = transition?.type == type, onClick = { actions.onType(type) }, badge = R.drawable.ic_play)
            }
        }
        val top = maxMs.coerceIn(MIN_TRANSITION_MS, MAX_TRANSITION_MS)
        DurationRow(transition?.durationMs ?: 0L, MIN_TRANSITION_MS, top, enabled = transition != null && top > MIN_TRANSITION_MS, onChanged = actions::onDuration)
    }
}

/** "Duration  ——●——  0.5s" in 0.1 s steps between [minMs] and [maxMs], applied on release. */
@Composable
fun DurationRow(durationMs: Long, minMs: Long, maxMs: Long, enabled: Boolean, onChanged: (Long) -> Unit) {
    var seconds by remember(durationMs, maxMs) { mutableFloatStateOf(durationMs.coerceIn(minMs, maxMs) / 1000f) }
    SliderRow(value = stringResource(R.string.motion_seconds, seconds), label = stringResource(R.string.motion_duration)) { modifier ->
        PreCutSlider(
            value = seconds,
            onValueChange = { seconds = (it * 10f).roundToLong() / 10f },
            onValueChangeFinished = { onChanged((seconds * 1000f).roundToLong()) },
            valueRange = minMs / 1000f..maxMs / 1000f,
            enabled = enabled,
            modifier = modifier,
        )
    }
}
