package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.editor.KeyframeButton
import com.savie.precut.media.PreviewState
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.PreCutTheme
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/**
 * The 48 dp playback row: "0:07 / 0:18" at the start, a filled Play/Pause in the middle, Full
 * screen at the end. The timeline is the scrubber.
 */
@Composable
fun PlaybackControls(
    state: PreviewState,
    onPlayPause: () -> Unit,
    onFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
    /** The keyframe button (P-05); null hides it. */
    keyframe: KeyframeButton? = null,
    onKeyframe: () -> Unit = {},
    onKeyframeEasing: () -> Unit = {},
) {
    val hasMedia = state.durationMs > 0L
    val colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary, disabledContentColor = TextDisabled)
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append(formatDurationMs(state.positionMs))
                withStyle(SpanStyle(color = TextSecondary)) { append(" / " + formatDurationMs(state.durationMs)) }
            },
            style = PreCutType.timecode,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        IconButton(onClick = onPlayPause, enabled = hasMedia, colors = colors, modifier = Modifier.align(Alignment.Center)) {
            Icon(
                painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                modifier = Modifier.size(32.dp),
            )
        }
        keyframe?.let { button ->
            val label = stringResource(if (button.atKeyframe) R.string.keyframe_remove else R.string.keyframe_add)
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 52.dp)
                    .size(48.dp)
                    .combinedClickable(
                        onClickLabel = label,
                        onLongClickLabel = stringResource(R.string.keyframe_easing),
                        onLongClick = onKeyframeEasing,
                        onClick = onKeyframe,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (button.atKeyframe) R.drawable.ic_diamond else R.drawable.ic_add_diamond),
                    label,
                    tint = if (button.atKeyframe) Accent else TextPrimary,
                )
            }
        }
        IconButton(onClick = onFullScreen, enabled = hasMedia, colors = colors, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(painterResource(R.drawable.ic_fullscreen), stringResource(R.string.preview_full_screen))
        }
    }
}

@Preview
@Composable
private fun PlaybackControlsPreview() {
    PreCutTheme {
        PlaybackControls(PreviewState(isPlaying = false, positionMs = 7_000L, durationMs = 18_000L), onPlayPause = {}, onFullScreen = {})
    }
}
