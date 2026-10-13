package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.savie.precut.ui.theme.TextPrimary

/** Record button: starts at the playhead, plays the (muted) preview while recording. */
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
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (recording == null) {
                    stringResource(R.string.voiceover_hint, formatPreciseMs(playheadMs))
                } else {
                    stringResource(R.string.voiceover_recording, formatPreciseMs(recording.elapsedMs))
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Box(
                Modifier
                    .size(64.dp)
                    .border(3.dp, TextPrimary, CircleShape)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = if (recording == null) onRecord else onStop),
                contentAlignment = Alignment.Center,
            ) {
                val shape = if (recording == null) CircleShape else RoundedCornerShape(6.dp)
                Box(
                    Modifier
                        .size(if (recording == null) 48.dp else 24.dp)
                        .background(MaterialTheme.colorScheme.primary, shape),
                )
            }
        }
    }
}
