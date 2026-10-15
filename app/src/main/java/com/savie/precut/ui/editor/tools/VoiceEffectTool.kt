package com.savie.precut.ui.editor.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import com.savie.precut.ui.components.PresetTile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.VoiceEffect

/** Pitch presets for an audio clip (length unchanged). */
@Composable
fun VoiceEffectTool(selected: VoiceEffect, onSelected: (VoiceEffect) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    ToolPanel(stringResource(R.string.voice_title), onDone, modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            VoiceEffect.entries.forEach { effect ->
                PresetTile(stringResource(effect.labelRes()), effect.iconRes(), selected = effect == selected, onClick = { onSelected(effect) })
            }
        }
    }
}

@StringRes
private fun VoiceEffect.labelRes(): Int = when (this) {
    VoiceEffect.NONE -> R.string.voice_none
    VoiceEffect.CHIPMUNK -> R.string.voice_chipmunk
    VoiceEffect.HIGH -> R.string.voice_high
    VoiceEffect.DEEP -> R.string.voice_deep
    VoiceEffect.MONSTER -> R.string.voice_monster
}

@DrawableRes
private fun VoiceEffect.iconRes(): Int = when (this) {
    VoiceEffect.NONE -> R.drawable.ic_block
    VoiceEffect.CHIPMUNK -> R.drawable.ic_pets
    VoiceEffect.HIGH -> R.drawable.ic_arrow_upward
    VoiceEffect.DEEP -> R.drawable.ic_arrow_downward
    VoiceEffect.MONSTER -> R.drawable.ic_skull
}
