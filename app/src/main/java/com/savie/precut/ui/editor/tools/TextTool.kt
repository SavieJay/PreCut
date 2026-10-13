package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.MAX_TEXT_SIZE_SP
import com.savie.precut.model.MIN_TEXT_SIZE_SP
import com.savie.precut.model.TextOverlay

/** What the text panel can do with the selected text. */
interface TextToolActions {
    fun onChange(overlay: TextOverlay)
    fun onSplit()
    fun onDuplicate()
    fun onDelete()
}

/** Edits the selected text overlay; every change is applied immediately (sliders on release). */
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
            IconButton(onClick = actions::onSplit, enabled = canSplit) {
                Icon(painterResource(R.drawable.ic_split), stringResource(R.string.tool_split))
            }
            IconButton(onClick = actions::onDuplicate) {
                Icon(painterResource(R.drawable.ic_duplicate), stringResource(R.string.tool_duplicate))
            }
            IconButton(onClick = actions::onDelete) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.text_delete))
            }
        },
    ) {
        OutlinedTextField(
            value = overlay.text,
            onValueChange = { actions.onChange(overlay.copy(text = it)) },
            placeholder = { Text(stringResource(R.string.text_placeholder)) },
            singleLine = false,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        LabelledSlider(stringResource(R.string.text_size), size, MIN_TEXT_SIZE_SP..MAX_TEXT_SIZE_SP, { size = it }) {
            actions.onChange(overlay.copy(textSizeSp = size))
        }
        LabelledSlider(stringResource(R.string.text_opacity), opacity, 0.1f..1f, { opacity = it }) {
            actions.onChange(overlay.copy(opacity = opacity))
        }
        TextStyleRow(overlay, recentColors, onChange = actions::onChange)
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
        Slider(value = value, onValueChange = onValueChange, onValueChangeFinished = onFinished, valueRange = range, modifier = Modifier.weight(1f))
    }
}
