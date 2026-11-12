package com.savie.precut.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.Ink0

/**
 * The 2026 switch (RD-TOK `.sw`): 52 × 32. On = pink (lime when [ai]) with a dark 24 dp thumb;
 * off = white 8% with a 28% ring and a small 60% white thumb.
 */
@Composable
fun PreCutSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, ai: Boolean = false) {
    val on = if (ai) AiAccent else Accent
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Ink0,
            checkedTrackColor = on,
            checkedBorderColor = on,
            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
            uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
            uncheckedBorderColor = Color.White.copy(alpha = 0.28f),
        ),
    )
}
