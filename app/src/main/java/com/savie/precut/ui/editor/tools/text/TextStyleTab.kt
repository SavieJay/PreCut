package com.savie.precut.ui.editor.tools.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.TextAlign
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/** Style (X-04b): bold, italic, underline; alignment; letter and line spacing (sliders on release). */
@Composable
fun TextStyleTab(overlay: TextOverlay, onChange: (TextOverlay) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Toggle(R.drawable.ic_format_bold, R.string.text_bold, overlay.bold) { onChange(overlay.copy(bold = it)) }
        Toggle(R.drawable.ic_format_italic, R.string.text_italic, overlay.italic) { onChange(overlay.copy(italic = it)) }
        Toggle(R.drawable.ic_format_underlined, R.string.text_underline, overlay.underline) { onChange(overlay.copy(underline = it)) }
        Toggle(R.drawable.ic_format_align_left, R.string.text_align_left, overlay.align == TextAlign.LEFT) { onChange(overlay.copy(align = TextAlign.LEFT)) }
        Toggle(R.drawable.ic_format_align_center, R.string.text_align_center, overlay.align == TextAlign.CENTER) { onChange(overlay.copy(align = TextAlign.CENTER)) }
        Toggle(R.drawable.ic_format_align_right, R.string.text_align_right, overlay.align == TextAlign.RIGHT) { onChange(overlay.copy(align = TextAlign.RIGHT)) }
    }
    var letters by remember(overlay.letterSpacing) { mutableFloatStateOf(overlay.letterSpacing) }
    SliderRow(value = letters.roundToInt().toString(), label = stringResource(R.string.text_letter_spacing)) { modifier ->
        PreCutSlider(
            value = letters,
            onValueChange = { letters = it.roundToInt().toFloat() },
            onValueChangeFinished = { onChange(overlay.copy(letterSpacing = letters)) },
            valueRange = -5f..20f,
            anchor = 0f,
            onReset = { onChange(overlay.copy(letterSpacing = 0f)) },
            modifier = modifier,
        )
    }
    var lines by remember(overlay.lineSpacing) { mutableFloatStateOf(overlay.lineSpacing) }
    SliderRow(value = String.format(Locale.ROOT, "%.1f", lines), label = stringResource(R.string.text_line_spacing)) { modifier ->
        PreCutSlider(
            value = lines,
            onValueChange = { lines = (it * 10).roundToInt() / 10f },
            onValueChangeFinished = { onChange(overlay.copy(lineSpacing = lines)) },
            valueRange = 0.8f..2f,
            onReset = { onChange(overlay.copy(lineSpacing = 1f)) },
            modifier = modifier,
        )
    }
}

@Composable
private fun Toggle(icon: Int, label: Int, on: Boolean, onChange: (Boolean) -> Unit) {
    IconToggleButton(checked = on, onCheckedChange = onChange) {
        Icon(painterResource(icon), contentDescription = stringResource(label), tint = if (on) Accent else TextSecondary)
    }
}
