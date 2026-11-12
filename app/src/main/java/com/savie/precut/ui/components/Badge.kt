package com.savie.precut.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.BadgeInk
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.tabular

/**
 * A small label over media (duration, speed, muted, "Reversed"; RD-CMP `.bd`): 20 dp, Base 0 at
 * 82%, 11 sp tabular; [ai] tints it lime for results of an AI tool.
 */
@Composable
fun MediaBadge(text: String? = null, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, contentDescription: String? = null, ai: Boolean = false) {
    val color = if (ai) AiAccent else TextPrimary
    Row(
        modifier
            .height(20.dp)
            .background(BadgeInk, CircleShape)
            .padding(horizontal = if (text == null) 4.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = contentDescription, tint = color, modifier = Modifier.size(13.dp)) }
        text?.let { Text(it, style = BadgeText, color = color, maxLines = 1) }
    }
}

private val BadgeText
    @Composable get() = MaterialTheme.typography.labelSmall.tabular().copy(fontSize = 11.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
