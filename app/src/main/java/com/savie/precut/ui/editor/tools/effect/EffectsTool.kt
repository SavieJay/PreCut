package com.savie.precut.ui.editor.tools.effect

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.effect.EffectCategory
import com.savie.precut.model.effect.EffectSettings
import com.savie.precut.model.effect.EffectType
import com.savie.precut.model.effect.MAX_EFFECT_SPEED
import com.savie.precut.model.effect.MIN_EFFECT_SPEED
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.PresetTile
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.components.TileGrid
import com.savie.precut.ui.editor.tools.ApplyToAllButton
import com.savie.precut.ui.editor.tools.ToolPanel
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/** What the Effects panel can do. */
interface EffectsActions {
    fun onPick(type: EffectType)
    fun onNone()
    fun onIntensity(intensity: Float)
    fun onSpeed(speed: Float)
    fun onApplyAll()
    fun onToggleFavourite(type: EffectType)
}

/**
 * S-03: Favourites and the six categories of effects, then Intensity and Speed for the chosen
 * one. [current] is the target's effect (null: none yet); [editing] is false while adding a new
 * effect, which hides None; [forClip] shows Apply to all.
 */
@Composable
fun EffectsTool(
    current: EffectSettings?,
    favourites: Set<EffectType>,
    editing: Boolean,
    forClip: Boolean,
    actions: EffectsActions,
    onDone: () -> Unit,
) {
    // Tab 0 is Favourites; the categories follow.
    var tab by rememberSaveable { mutableIntStateOf(current?.type?.category?.let { it.ordinal + 1 } ?: 1) }
    ToolPanel(
        stringResource(R.string.effects_title),
        onDone,
        actions = {
            if (forClip && current != null) ApplyToAllButton(actions::onApplyAll)
            current?.let { effect ->
                val starred = effect.type in favourites
                IconButton(onClick = { actions.onToggleFavourite(effect.type) }) {
                    Icon(
                        painterResource(if (starred) R.drawable.ic_star_filled else R.drawable.ic_star),
                        contentDescription = stringResource(if (starred) R.string.effects_favourite_remove else R.string.effects_favourite_add),
                        tint = if (starred) Accent else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) {
        val tabs = listOf(PillTab(stringResource(R.string.effects_favourites))) + EffectCategory.entries.map { PillTab(stringResource(it.label())) }
        PillTabs(tabs, tab, { tab = it }, Modifier.horizontalScroll(rememberScrollState()))
        val types = if (tab == 0) EffectType.entries.filter { it in favourites } else EffectType.entries.filter { it.category.ordinal == tab - 1 }
        if (tab == 0 && types.isEmpty()) {
            Text(stringResource(R.string.effects_no_favourites), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        } else {
            TileGrid((if (editing) listOf<EffectType?>(null) else emptyList()) + types, columns = 4) { type ->
                if (type == null) {
                    PresetTile(stringResource(R.string.effects_none), R.drawable.ic_block, selected = current == null, onClick = actions::onNone)
                } else {
                    PresetTile(stringResource(type.label()), type.icon(), selected = current?.type == type, onClick = { actions.onPick(type) }, badge = R.drawable.ic_play)
                }
            }
        }
        EffectSliders(current, actions)
    }
}

@Composable
private fun EffectSliders(current: EffectSettings?, actions: EffectsActions) {
    val enabled = current != null
    var intensity by remember(current?.intensity) { mutableFloatStateOf(current?.intensity ?: 0f) }
    var speed by remember(current?.speed) { mutableFloatStateOf(current?.speed ?: 1f) }
    SliderRow(value = if (enabled) "${(intensity * 100).roundToInt()}" else "–", label = stringResource(R.string.effects_intensity)) { m ->
        PreCutSlider(value = intensity, onValueChange = { intensity = it }, onValueChangeFinished = { actions.onIntensity(intensity) }, enabled = enabled, modifier = m)
    }
    SliderRow(value = if (enabled) String.format(Locale.ROOT, "%.1fx", speed) else "–", label = stringResource(R.string.effects_speed)) { m ->
        PreCutSlider(
            value = speed, onValueChange = { speed = it }, onValueChangeFinished = { actions.onSpeed(speed) },
            valueRange = MIN_EFFECT_SPEED..MAX_EFFECT_SPEED, onReset = { actions.onSpeed(1f) }, enabled = enabled, modifier = m,
        )
    }
}
