package com.savie.precut.ui.editor.tools.effect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.editor.tools.ToolPanel
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.RowFill
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glassRow

/** S-04: put the picked effect on the clip under the playhead, or on a new layer over everything. */
@Composable
fun EffectTargetTool(effectName: String, clipNote: String, layerNote: String, onApply: (clip: Boolean) -> Unit, onBack: () -> Unit) {
    var clip by rememberSaveable { mutableStateOf(true) }
    ToolPanel(stringResource(R.string.effects_apply_to, effectName), onDone = { onApply(clip) }, onBack = onBack) {
        TargetOption(R.drawable.ic_movie, stringResource(R.string.effects_this_clip), clipNote, selected = clip) { clip = true }
        TargetOption(R.drawable.ic_overlay, stringResource(R.string.effects_everything), layerNote, selected = !clip) { clip = false }
        FilledButton(stringResource(R.string.effects_apply), onClick = { onApply(clip) }, modifier = Modifier.fillMaxWidth(), large = true)
    }
}

@Composable
private fun TargetOption(icon: Int, title: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(OptionShape)
            .then(if (selected) Modifier.background(Accent.copy(alpha = 0.08f)).border(2.dp, Accent, OptionShape) else Modifier.glassRow(OptionShape))
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(if (selected) Accent else RowFill, IconShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = if (selected) OnAccent else TextPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(note, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Box(
            Modifier
                .size(22.dp)
                .border(if (selected) 6.dp else 2.dp, if (selected) Accent else Color.White.copy(alpha = 0.4f), CircleShape),
        )
    }
}

private val OptionShape = RoundedCornerShape(22.dp)
private val IconShape = RoundedCornerShape(16.dp)
