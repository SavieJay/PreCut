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
import com.savie.precut.model.Clip

/** Volume of a clip's own sound, plus mute (which keeps the volume for unmuting). */
@Composable
fun ClipVolumeTool(
    clip: Clip,
    onVolumeChanged: (Float) -> Unit,
    onMutedChanged: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(stringResource(R.string.volume_title), onDone, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VolumeSlider(clip.volume, enabled = !clip.muted, onVolumeChanged = onVolumeChanged, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.volume_mute), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Switch(checked = clip.muted, onCheckedChange = onMutedChanged)
        }
    }
}
