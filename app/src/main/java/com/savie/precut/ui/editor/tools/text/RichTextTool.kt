package com.savie.precut.ui.editor.tools.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.MAX_TEXT_SIZE_SP
import com.savie.precut.model.MIN_TEXT_SIZE_SP
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.TextSpan
import com.savie.precut.model.withSpanStyle
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.editor.tools.ColorSwatchRow
import com.savie.precut.ui.editor.tools.ToolPanel
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Rich text (X-05): select words in the field, then size, colour and bold/italic/underline apply
 * to that range only; Clear removes its own styling.
 */
@Composable
fun RichTextTool(overlay: TextOverlay, recentColors: List<Int>, onChange: (TextOverlay) -> Unit, onBack: () -> Unit, onDone: () -> Unit) {
    var field by remember(overlay.id) { mutableStateOf(TextFieldValue(overlay.text, TextRange(0, overlay.text.length))) }
    val start = minOf(field.selection.start, field.selection.end).coerceIn(0, overlay.text.length)
    val end = maxOf(field.selection.start, field.selection.end).coerceIn(0, overlay.text.length)
    val picked = end > start
    fun style(span: TextSpan) {
        if (picked) onChange(overlay.withSpanStyle(start, end, span))
    }
    val current = overlay.spans.firstOrNull { it.start <= start && it.end >= end && picked }
    ToolPanel(stringResource(R.string.text_rich_title), onDone, onBack = onBack) {
        // The words stay as they are here; this field only picks the range.
        OutlinedTextField(
            value = field,
            onValueChange = { field = it.copy(text = overlay.text) },
            readOnly = true,
            shape = RoundedCornerShape(8.dp),
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_text_select_start), contentDescription = null, tint = TextSecondary)
            Text(
                if (picked) stringResource(R.string.text_rich_styling, overlay.text.substring(start, end)) else stringResource(R.string.text_rich_hint),
                style = PreCutType.labelChip,
                color = TextSecondary,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            AccentTextButton(stringResource(R.string.text_rich_clear), onClick = {
                onChange(overlay.copy(spans = overlay.spans.filter { it.end <= start || it.start >= end }))
            }, enabled = picked, color = TextSecondary)
        }
        var size by remember(current?.sizeSp, overlay.textSizeSp) { mutableFloatStateOf(current?.sizeSp ?: overlay.textSizeSp) }
        SliderRow(value = size.roundToInt().toString(), label = stringResource(R.string.text_size)) { modifier ->
            PreCutSlider(
                value = size,
                onValueChange = { size = it },
                onValueChangeFinished = { style(TextSpan(start, end, sizeSp = size)) },
                valueRange = MIN_TEXT_SIZE_SP..MAX_TEXT_SIZE_SP,
                enabled = picked,
                modifier = modifier,
            )
        }
        ColorSwatchRow(current?.colorArgb, recentColors) { style(TextSpan(start, end, colorArgb = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StyleChip(R.drawable.ic_format_bold, R.string.text_bold, current?.bold ?: overlay.bold, picked) { style(TextSpan(start, end, bold = it)) }
            StyleChip(R.drawable.ic_format_italic, R.string.text_italic, current?.italic ?: overlay.italic, picked) { style(TextSpan(start, end, italic = it)) }
            StyleChip(R.drawable.ic_format_underlined, R.string.text_underline, current?.underline ?: overlay.underline, picked) { style(TextSpan(start, end, underline = it)) }
        }
    }
}

@Composable
private fun StyleChip(icon: Int, label: Int, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = on,
        onClick = { onChange(!on) },
        enabled = enabled,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
    )
}
