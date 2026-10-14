package com.savie.precut.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.tabular

/** Small label over media (duration, speed, muted): 16 dp, Ink0 at 82%, 10 sp tabular. */
@Composable
fun MediaBadge(text: String? = null, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, contentDescription: String? = null) {
    Row(
        modifier
            .height(16.dp)
            .background(BadgeBackground, RoundedCornerShape(4.dp))
            .padding(horizontal = if (text == null) 2.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = contentDescription, tint = TextPrimary, modifier = Modifier.size(12.dp)) }
        text?.let { Text(it, style = MaterialTheme.typography.labelSmall.tabular(), color = TextPrimary) }
    }
}

private val BadgeBackground = Color(0xD10E0E10)
