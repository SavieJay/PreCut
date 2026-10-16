package com.savie.precut.ui.editor.tools.effect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.editor.tools.ToolPanel
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.TextSecondary

/** S-04: put the picked effect on the clip under the playhead, or on a new layer over everything. */
@Composable
fun EffectTargetTool(effectName: String, clipNote: String, layerNote: String, onApply: (clip: Boolean) -> Unit, onBack: () -> Unit) {
    var clip by rememberSaveable { mutableStateOf(true) }
    ToolPanel(stringResource(R.string.effects_apply_to, effectName), onDone = { onApply(clip) }, onBack = onBack) {
        TargetOption(stringResource(R.string.effects_this_clip), clipNote, selected = clip) { clip = true }
        TargetOption(stringResource(R.string.effects_everything), layerNote, selected = !clip) { clip = false }
        FilledButton(stringResource(R.string.effects_apply), onClick = { onApply(clip) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TargetOption(title: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Accent))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
