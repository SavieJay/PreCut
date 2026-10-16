package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.model.clipTimelineRange
import com.savie.precut.model.effect.DEFAULT_EFFECT_LAYER_MS
import com.savie.precut.model.effect.EffectLayer
import com.savie.precut.model.effect.EffectType
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.editor.tools.effect.EffectLayerActions
import com.savie.precut.ui.editor.tools.effect.EffectLayerToolbar
import com.savie.precut.ui.editor.tools.effect.EffectTargetTool
import com.savie.precut.ui.editor.tools.effect.EffectsActions
import com.savie.precut.ui.editor.tools.effect.EffectsTool
import com.savie.precut.ui.editor.tools.effect.label

/** The Effects panel (S-03) or its Apply-to choice (S-04). */
@Composable
fun EffectPanels(state: EditorUiState.Ready, tool: EditorTool, positionMs: () -> Long, viewModel: EditorViewModel) {
    val effects = viewModel.effects
    if (tool == EditorTool.EFFECT_TARGET) {
        val pending by effects.pending.collectAsStateWithLifecycle()
        val effect = pending ?: return
        val clipRange = viewModel.targetClipIdForEffects()?.let { state.project.clipTimelineRange(it) }
        EffectTargetTool(
            effectName = stringResource(effect.type.label()),
            clipNote = stringResource(com.savie.precut.R.string.effects_this_clip_note, formatSeconds(clipRange?.let { it.last - it.first } ?: 0L)),
            layerNote = stringResource(com.savie.precut.R.string.effects_everything_note, formatSeconds(DEFAULT_EFFECT_LAYER_MS), formatDurationMs(positionMs())),
            onApply = effects::onApplyTo,
            onBack = effects::onCancelTarget,
        )
        return
    }
    val favourites by effects.favourites.collectAsStateWithLifecycle()
    val target = effects.target(state)
    val actions = remember(effects) {
        object : EffectsActions {
            override fun onPick(type: EffectType) = effects.onPick(type)
            override fun onNone() = effects.onNone()
            override fun onIntensity(intensity: Float) = effects.onIntensity(intensity)
            override fun onSpeed(speed: Float) = effects.onSpeed(speed)
            override fun onApplyAll() = effects.onApplyAll()
            override fun onToggleFavourite(type: EffectType) = effects.onToggleFavourite(type)
        }
    }
    EffectsTool(
        current = effects.current(state),
        favourites = favourites,
        editing = target != EffectTarget.New,
        forClip = target is EffectTarget.Clip,
        actions = actions,
        onDone = viewModel::onToolClosed,
    )
}

/** The selected effect layer's toolbar. */
@Composable
fun EffectLayerPanel(layer: EffectLayer, positionMs: () -> Long, viewModel: EditorViewModel) {
    val effects = viewModel.effects
    val haptics = LocalHapticFeedback.current
    EffectLayerToolbar(
        canSplit = rememberCanSplit(positionMs, layer.startMs, layer.endMs),
        actions = remember(effects) {
            object : EffectLayerActions {
                override fun onEffect() = effects.open()
                override fun onSplit() {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    effects.onSplitLayer()
                }
                override fun onDuplicate() = effects.onDuplicateLayer()
                override fun onDelete() {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    effects.onDeleteLayer()
                }
            }
        },
    )
}

/** Tools shown by [EffectPanels]. */
val EFFECT_TOOLS = setOf(EditorTool.EFFECTS, EditorTool.EFFECT_TARGET)
