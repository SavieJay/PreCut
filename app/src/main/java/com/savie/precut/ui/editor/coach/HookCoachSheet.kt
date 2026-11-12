package com.savie.precut.ui.editor.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.coach.HookTip
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.editor.HookCoachUi
import com.savie.precut.ui.theme.GlassSolid
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** Hook coach (E5 in spirit): two to four concrete tips, each with Apply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HookCoachSheet(ui: HookCoachUi, onApply: (HookTip, String) -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(R.string.hook_default_title)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Solid, not glass: the sheet rises over the editor's video preview.
        containerColor = GlassSolid,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.hook_title), style = PreCutType.panelTitle, color = TextPrimary)
            if (ui.tips.isEmpty()) {
                Text(stringResource(R.string.hook_all_good), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            ui.tips.forEach { tip ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(tip.headline()), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(stringResource(tip.why()), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    val applied = tip in ui.applied
                    AccentTextButton(stringResource(if (applied) R.string.hook_applied else R.string.hook_apply), { onApply(tip, title) }, enabled = !applied)
                }
            }
        }
    }
}

private fun HookTip.headline(): Int = when (this) {
    HookTip.AddTitle -> R.string.hook_tip_title
    is HookTip.CutQuietStart -> R.string.hook_tip_quiet
    is HookTip.TrimFirstClip -> R.string.hook_tip_trim
    is HookTip.ZoomInFirstClip -> R.string.hook_tip_zoom
    is HookTip.CutToBeats -> R.string.hook_tip_beats
}

private fun HookTip.why(): Int = when (this) {
    HookTip.AddTitle -> R.string.hook_why_title
    is HookTip.CutQuietStart -> R.string.hook_why_quiet
    is HookTip.TrimFirstClip -> R.string.hook_why_trim
    is HookTip.ZoomInFirstClip -> R.string.hook_why_zoom
    is HookTip.CutToBeats -> R.string.hook_why_beats
}
