package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.TextPrimary

internal val MuteButtonSize = 36.dp

/** CapCut's "Mute clip audio" switch, pinned at the left of the clip strip. */
@Composable
fun MuteOriginalButton(muted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(MuteButtonSize)
            .clip(CircleShape)
            .background(Ink2.copy(alpha = 0.9f))
            .clickable(role = Role.Switch, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume),
            contentDescription = stringResource(if (muted) R.string.audio_unmute_clips else R.string.audio_mute_clips),
            tint = if (muted) Accent else TextPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}
