package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TrackLabel
import com.savie.precut.ui.theme.VideoBar

/**
 * CapCut's "Mute clip audio" switch, as the main strip's gutter tile (RD-E03 `.ti`): the pink video
 * tile with a speaker, or the quiet outlined tile with a crossed speaker once the clips are muted.
 * The touch target is 40 dp, reaching past the 28 dp tile.
 */
@Composable
fun MuteOriginalButton(muted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (muted) R.string.audio_unmute_clips else R.string.audio_mute_clips)
    Box(
        modifier
            .size(MuteButtonSize)
            .clip(CircleShape)
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.CenterStart,
    ) {
        TrackTile(
            color = VideoBar,
            icon = if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume,
            outlined = muted,
            iconColor = if (muted) TextPrimary else TrackLabel,
        )
    }
}

internal val MuteButtonSize = 40.dp
