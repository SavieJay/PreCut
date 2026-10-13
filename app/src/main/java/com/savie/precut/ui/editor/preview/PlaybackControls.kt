package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.PreviewState
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.PreCutTheme
import com.savie.precut.ui.theme.TextSecondary

/** Time readout, play/pause and full screen between the preview and the timeline; the timeline is the scrubber. */
@Composable
fun PlaybackControls(
    state: PreviewState,
    onPlayPause: () -> Unit,
    onFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append(formatDurationMs(state.positionMs))
                withStyle(MaterialTheme.typography.labelMedium.toSpanStyle().copy(color = TextSecondary)) {
                    append(" / " + formatDurationMs(state.durationMs))
                }
            },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        IconButton(onClick = onPlayPause, modifier = Modifier.align(Alignment.Center)) {
            if (state.isPlaying) {
                Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.action_pause))
            } else {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.action_play))
            }
        }
        IconButton(onClick = onFullScreen, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(painterResource(R.drawable.ic_fullscreen), stringResource(R.string.preview_full_screen))
        }
    }
}

@Preview
@Composable
private fun PlaybackControlsPreview() {
    PreCutTheme {
        PlaybackControls(PreviewState(isPlaying = false, positionMs = 12_000L, durationMs = 65_000L), onPlayPause = {}, onFullScreen = {})
    }
}
