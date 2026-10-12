package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.PreviewState
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.PreCutTheme

@Composable
fun PlaybackControls(
    state: PreviewState,
    onPlayPause: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While dragging, show the thumb where the finger is rather than the player's lagging position.
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }
    val shownPositionMs = dragPositionMs ?: state.positionMs.toFloat()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onPlayPause) {
            if (state.isPlaying) {
                Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.action_pause))
            } else {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.action_play))
            }
        }
        Text(formatDurationMs(shownPositionMs.toLong()), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = shownPositionMs,
            onValueChange = {
                dragPositionMs = it
                onSeek(it.toLong())
            },
            onValueChangeFinished = { dragPositionMs = null },
            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
            enabled = state.durationMs > 0L,
            modifier = Modifier.weight(1f),
        )
        Text(formatDurationMs(state.durationMs), style = MaterialTheme.typography.labelMedium)
    }
}

@Preview
@Composable
private fun PlaybackControlsPreview() {
    PreCutTheme {
        PlaybackControls(
            state = PreviewState(isPlaying = false, positionMs = 12_000L, durationMs = 65_000L),
            onPlayPause = {},
            onSeek = {},
        )
    }
}
