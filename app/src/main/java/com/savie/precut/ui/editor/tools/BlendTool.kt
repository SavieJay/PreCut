package com.savie.precut.ui.editor.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.frame.BlendMode
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.theme.TextSecondary

/** Blend (CapCut's overlay Blend): how the overlay mixes with what's under it; a pick applies at once. */
@Composable
fun BlendTool(mode: BlendMode, onPicked: (BlendMode) -> Unit, onDone: () -> Unit) {
    ToolPanel(stringResource(R.string.tool_blend), onDone) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlendMode.entries.forEach { option ->
                PreCutChip(stringResource(option.label), selected = option == mode, onClick = { onPicked(option) })
            }
        }
        Text(stringResource(R.string.blend_hint), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

private val BlendMode.label: Int
    @StringRes get() = when (this) {
        BlendMode.NORMAL -> R.string.blend_normal
        BlendMode.MULTIPLY -> R.string.blend_multiply
        BlendMode.SCREEN -> R.string.blend_screen
        BlendMode.OVERLAY -> R.string.blend_overlay
        BlendMode.DARKEN -> R.string.blend_darken
        BlendMode.LIGHTEN -> R.string.blend_lighten
        BlendMode.ADD -> R.string.blend_add
        BlendMode.SOFT_LIGHT -> R.string.blend_soft_light
        BlendMode.DIFFERENCE -> R.string.blend_difference
    }
