package com.savie.precut.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.savie.precut.ui.theme.ErrorRed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.theme.ErrorContainer
import com.savie.precut.ui.theme.OnErrorContainer
import com.savie.precut.ui.theme.PreCutType

private val BannerShape = RoundedCornerShape(20.dp)

/** Shown when some media can't be opened (RD-E06 `.ban`, a rounded red card under the top bar): select a red clip to Replace it, or remove them all. */
@Composable
fun MissingMediaBanner(count: Int, onRemoveAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .clip(BannerShape)
            .background(ErrorContainer)
            .background(Brush.linearGradient(listOf(ErrorRed.copy(alpha = 0.26f), ErrorRed.copy(alpha = 0.08f))))
            .border(1.dp, ErrorRed.copy(alpha = 0.35f), BannerShape)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = OnErrorContainer, modifier = Modifier.size(20.dp))
        Text(
            pluralStringResource(R.plurals.missing_media, count, count),
            style = PreCutType.labelChip,
            color = OnErrorContainer,
            modifier = Modifier.weight(1f),
        )
        AccentTextButton(stringResource(R.string.missing_remove_all), onRemoveAll, color = OnErrorContainer)
    }
}
