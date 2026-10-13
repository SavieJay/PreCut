package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.components.formatSpeed

/** Small labels over a clip's top-left: duration (when selected), speed (when not 1x), muted. */
@Composable
fun ClipBadges(speed: Float, durationMs: Long?, soundOff: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 16.dp, top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        durationMs?.let { Badge(formatSeconds(it)) }
        if (speed != 1f) Badge(stringResource(R.string.speed_value, formatSpeed(speed)))
        if (soundOff) {
            Icon(
                painterResource(R.drawable.ic_volume_off),
                contentDescription = stringResource(R.string.volume_muted),
                tint = Color.White,
                modifier = Modifier
                    .size(14.dp)
                    .background(BadgeBackground, RoundedCornerShape(7.dp)),
            )
        }
    }
}

private val BadgeBackground = Color.Black.copy(alpha = 0.55f)

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .background(BadgeBackground, RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp),
    )
}
