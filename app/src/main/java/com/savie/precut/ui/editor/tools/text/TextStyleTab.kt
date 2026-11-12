package com.savie.precut.ui.editor.tools.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.TextAlign
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import java.util.Locale
import kotlin.math.roundToInt

/** Style (X-04b): bold, italic, underline; alignment; letter and line spacing (sliders on release). */
@Composable
fun TextStyleTab(overlay: TextOverlay, onChange: (TextOverlay) -> Unit) {
    IconSegments(
        listOf(
            IconSegment(R.drawable.ic_format_bold, stringResource(R.string.text_bold), overlay.bold) { onChange(overlay.copy(bold = it)) },
            IconSegment(R.drawable.ic_format_italic, stringResource(R.string.text_italic), overlay.italic) { onChange(overlay.copy(italic = it)) },
            IconSegment(R.drawable.ic_format_underlined, stringResource(R.string.text_underline), overlay.underline) { onChange(overlay.copy(underline = it)) },
        ),
    )
    IconSegments(
        listOf(
            IconSegment(R.drawable.ic_format_align_left, stringResource(R.string.text_align_left), overlay.align == TextAlign.LEFT) { onChange(overlay.copy(align = TextAlign.LEFT)) },
            IconSegment(R.drawable.ic_format_align_center, stringResource(R.string.text_align_center), overlay.align == TextAlign.CENTER) { onChange(overlay.copy(align = TextAlign.CENTER)) },
            IconSegment(R.drawable.ic_format_align_right, stringResource(R.string.text_align_right), overlay.align == TextAlign.RIGHT) { onChange(overlay.copy(align = TextAlign.RIGHT)) },
        ),
    )
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

