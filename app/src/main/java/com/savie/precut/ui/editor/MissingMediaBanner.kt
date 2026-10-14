package com.savie.precut.ui.editor

import androidx.compose.foundation.background
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

/** Shown when some media can't be opened (E-06): select a red clip to Replace it, or remove them all. */
@Composable
fun MissingMediaBanner(count: Int, onRemoveAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(ErrorContainer)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
