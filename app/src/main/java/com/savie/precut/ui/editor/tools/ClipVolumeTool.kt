package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.SwitchRow
import com.savie.precut.model.Clip

/** Volume of a clip's own sound, plus mute (which keeps the volume for unmuting). */
@Composable
fun ClipVolumeTool(
    clip: Clip,
    onVolumeChanged: (Float) -> Unit,
    onMutedChanged: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shows "Apply to all" when set. */
    onApplyAll: (() -> Unit)? = null,
) {
    ToolPanel(stringResource(R.string.volume_title), onDone, modifier, actions = { onApplyAll?.let { ApplyToAllButton(it) } }) {
        VolumeSlider(clip.volume, enabled = !clip.muted, onVolumeChanged = onVolumeChanged)
        SwitchRow(stringResource(R.string.volume_mute), checked = clip.muted, onCheckedChange = onMutedChanged)
    }
}
