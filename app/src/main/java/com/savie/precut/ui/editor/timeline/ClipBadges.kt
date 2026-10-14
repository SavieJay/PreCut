package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.MediaBadge
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.components.formatSpeed

/** Badges at a clip's top-left: duration (when selected), speed (when not 1x), muted. */
@Composable
fun ClipBadges(speed: Float, durationMs: Long?, soundOff: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        durationMs?.let { MediaBadge(formatSeconds(it)) }
        if (speed != 1f) MediaBadge(stringResource(R.string.speed_value, formatSpeed(speed)))
        if (soundOff) MediaBadge(icon = R.drawable.ic_volume_off, contentDescription = stringResource(R.string.volume_muted))
    }
}
