package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.MAX_TEXT_SIZE_SP
import com.savie.precut.model.MIN_TEXT_SIZE_SP
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.components.SwitchRow
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** What the text panel can do with the selected text. */
interface TextToolActions {
    fun onChange(overlay: TextOverlay)
    fun onSplit()
    fun onDuplicate()
    fun onDelete()
}

/**
 * X-01: the text field, Size and Opacity, colours (custom + 8 swatches) and Background; Split,
 * Duplicate and Delete in the header. Edits apply immediately (sliders on release).
 */
@Composable
fun TextTool(
    overlay: TextOverlay,
    canSplit: Boolean,
    /** Colours already used by the project's texts, for the picker. */
    recentColors: List<Int>,
    actions: TextToolActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember(overlay.id, overlay.textSizeSp) { mutableFloatStateOf(overlay.textSizeSp) }
    var opacity by remember(overlay.id, overlay.opacity) { mutableFloatStateOf(overlay.opacity) }
    ToolPanel(
        stringResource(R.string.text_title),
        onDone,
        modifier,
        actions = {
            HeaderIcon(R.drawable.ic_split, stringResource(R.string.tool_split), canSplit, actions::onSplit)
            HeaderIcon(R.drawable.ic_duplicate, stringResource(R.string.tool_duplicate), true, actions::onDuplicate)
            HeaderIcon(R.drawable.ic_delete, stringResource(R.string.text_delete), true, actions::onDelete)
        },
    ) {
        OutlinedTextField(
            value = overlay.text,
            onValueChange = { actions.onChange(overlay.copy(text = it)) },
            placeholder = { Text(stringResource(R.string.text_placeholder)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Outline,
                focusedContainerColor = Ink0,
                unfocusedContainerColor = Ink0,
                cursorColor = Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        SliderRow(value = size.roundToInt().toString(), label = stringResource(R.string.text_size)) { sliderModifier ->
            PreCutSlider(
                value = size,
                onValueChange = { size = it },
                onValueChangeFinished = { actions.onChange(overlay.copy(textSizeSp = size)) },
                valueRange = MIN_TEXT_SIZE_SP..MAX_TEXT_SIZE_SP,
                modifier = sliderModifier,
            )
        }
        SliderRow(value = "${(opacity * 100).roundToInt()}%", label = stringResource(R.string.text_opacity)) { sliderModifier ->
            PreCutSlider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = { actions.onChange(overlay.copy(opacity = opacity)) },
                valueRange = 0.1f..1f,
                onReset = { actions.onChange(overlay.copy(opacity = 1f)) },
                modifier = sliderModifier,
            )
        }
        TextColorRow(overlay, recentColors, onChange = actions::onChange)
        SwitchRow(
            label = stringResource(R.string.text_background),
            checked = overlay.backgroundArgb != null,
            onCheckedChange = { on -> actions.onChange(overlay.copy(backgroundArgb = if (on) backgroundFor(overlay.colorArgb) else null)) },
        )
    }
}

@Composable
private fun HeaderIcon(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = description, tint = if (enabled) MaterialTheme.colorScheme.onSurface else TextSecondary, modifier = Modifier.size(20.dp))
    }
}
