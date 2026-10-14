package com.savie.precut.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.savie.precut.ui.theme.AccentFill
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Outline

/** The design's switch: 52 × 32; on = AccentFill with a white thumb, off = Ink2 with an Outline edge. */
@Composable
fun PreCutSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = AccentFill,
            checkedBorderColor = AccentFill,
            uncheckedThumbColor = UncheckedThumb,
            uncheckedTrackColor = Ink2,
            uncheckedBorderColor = Outline,
        ),
    )
}

private val UncheckedThumb = Color(0xFF8A8A93)
