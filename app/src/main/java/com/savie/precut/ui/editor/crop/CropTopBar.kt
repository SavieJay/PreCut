package com.savie.precut.ui.editor.crop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent

/** Crop mode's top bar (T-12b): ✕ cancels, ✓ applies. */
@Composable
fun CropTopBar(onCancel: () -> Unit, onApply: () -> Unit) {
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.crop_cancel))
        }
        Text(
            stringResource(R.string.crop_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onApply) {
            Icon(painterResource(R.drawable.ic_check), stringResource(R.string.crop_apply), tint = Accent)
        }
    }
}
