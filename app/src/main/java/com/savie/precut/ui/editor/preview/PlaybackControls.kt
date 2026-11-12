package com.savie.precut.ui.editor.preview

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.components.pressScale
import com.savie.precut.ui.theme.PreCutTheme
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary

/**
 * The 56 dp transport row (RD-E03, redline 3): "0:07.0 / 0:18.0" in tabular figures at the start,
 * a 48 dp white Play/Pause disc in the middle, the keyframe diamond and Full screen at the end. The
 * timeline is the scrubber.
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
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 20.dp, end = 8.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append(formatPreciseMs(state.positionMs))
                withStyle(SpanStyle(color = TextMuted)) { append(" / " + formatPreciseMs(state.durationMs)) }
            },
            style = PreCutType.timecode,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        PlayButton(state.isPlaying, hasMedia, onPlayPause, Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            keyframe?.let { button ->
                val label = stringResource(if (button.atKeyframe) R.string.keyframe_remove else R.string.keyframe_add)
                Box(
                    Modifier
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
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            PreCutIconButton(R.drawable.ic_fullscreen, stringResource(R.string.preview_full_screen), onFullScreen, small = true, enabled = hasMedia)
        }
    }
}

/** The white 48 dp disc with a dark 30 dp Play or Pause; 35% while there's nothing to play. */
@Composable
private fun PlayButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(if (playing) R.string.action_pause else R.string.action_play)
    Box(
        modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .pressScale(interaction)
            .shadow(6.dp, CircleShape, ambientColor = Color.Transparent, spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(CircleShape)
            .background(PlayDisc)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play), label, tint = Ink0, modifier = Modifier.size(30.dp))
    }
}

private val PlayDisc = Color.White

@Preview
@Composable
private fun PlaybackControlsPreview() {
    PreCutTheme {
        PlaybackControls(PreviewState(isPlaying = false, positionMs = 7_000L, durationMs = 18_000L), onPlayPause = {}, onFullScreen = {})
    }
}
