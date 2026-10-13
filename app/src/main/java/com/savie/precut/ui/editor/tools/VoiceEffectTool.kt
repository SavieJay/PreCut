package com.savie.precut.ui.editor.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
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
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceEffect.entries.forEach { effect ->
                FilterChip(
                    selected = effect == selected,
                    onClick = { onSelected(effect) },
                    label = { Text(stringResource(effect.labelRes())) },
                )
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
