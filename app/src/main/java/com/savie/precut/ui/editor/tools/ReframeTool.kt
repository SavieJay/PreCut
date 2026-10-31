package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.AspectRatio
import com.savie.precut.model.frame.ReframeSpeed
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
import com.savie.precut.ui.components.SwitchRow
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextSecondary

/** What the Auto reframe panel reports. */
interface ReframeActions {
    fun onSpeed(speed: ReframeSpeed)
    fun onApply()
    fun onApplyAll()
    fun onShowOriginal()
}

/**
 * Auto reframe (Phase 22B): how eagerly the frame follows the person, Reframe this clip (or Apply
 * to all, in the header), and Show original framing for comparing.
 */
@Composable
fun ReframeTool(ratio: AspectRatio, speed: ReframeSpeed, showingOriginal: Boolean, actions: ReframeActions, onDone: () -> Unit) {
    ToolPanel(
        stringResource(R.string.reframe_title),
        onDone,
        actions = { AccentTextButton(stringResource(R.string.action_apply_to_all), onClick = actions::onApplyAll, color = TextSecondary) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.reframe_intro, ratio.label()), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reframe_follow), style = PreCutType.labelChip, color = TextSecondary)
                val speeds = ReframeSpeed.entries
                PillTabs(
                    tabs = speeds.map { PillTab(stringResource(it.label())) },
                    selected = speeds.indexOf(speed),
                    onSelect = { actions.onSpeed(speeds[it]) },
                )
            }
            FilledButton(stringResource(R.string.reframe_apply), onClick = actions::onApply, modifier = Modifier.fillMaxWidth())
            SwitchRow(stringResource(R.string.reframe_show_original), checked = showingOriginal, onCheckedChange = { actions.onShowOriginal() })
            Text(stringResource(R.string.reframe_limits), style = PreCutType.labelChip, color = TextSecondary)
        }
    }
}

private fun ReframeSpeed.label(): Int = when (this) {
    ReframeSpeed.SLOW -> R.string.reframe_slow
    ReframeSpeed.NORMAL -> R.string.reframe_normal
    ReframeSpeed.FAST -> R.string.reframe_fast
}
