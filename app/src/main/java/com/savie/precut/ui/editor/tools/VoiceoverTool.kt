package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.editor.RecordingState
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/**
 * U-05: a big timer and an 80 dp record button (red disc, a rounded square while recording).
 * Recording starts at the playhead; the preview plays muted meanwhile.
 */
@Composable
fun VoiceoverTool(
    recording: RecordingState?,
    playheadMs: Long,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(stringResource(R.string.voiceover_title), onDone = { if (recording == null) onDone() }, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                formatPreciseMs(recording?.elapsedMs ?: 0L),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = if (recording != null) TextPrimary else TextSecondary,
            )
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(4.dp, TextPrimary, CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(if (recording == null) R.string.voiceover_start else R.string.voiceover_stop),
                        onClick = if (recording == null) onRecord else onStop,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (recording == null) 60.dp else 28.dp)
                        .background(Accent, if (recording == null) CircleShape else RoundedCornerShape(6.dp)),
                )
            }
            if (recording == null) {
                Text(stringResource(R.string.voiceover_hint, formatPreciseMs(playheadMs)), style = MaterialTheme.typography.bodySmall.tabular(), color = TextSecondary)
            }
        }
    }
}
