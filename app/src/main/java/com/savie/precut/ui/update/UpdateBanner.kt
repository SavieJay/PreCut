package com.savie.precut.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.theme.Ink2

/** Home's "a new version is out" banner (Phase 23); once Play has downloaded it, Restart installs it. */
@Composable
fun UpdateBanner(downloaded: Boolean, onUpdate: () -> Unit, onRestart: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).background(Ink2, RoundedCornerShape(12.dp)).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(if (downloaded) R.string.update_downloaded else R.string.update_available),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        if (downloaded) {
            AccentTextButton(stringResource(R.string.update_restart), onRestart)
        } else {
            AccentTextButton(stringResource(R.string.update_action), onUpdate)
        }
    }
}
