package com.savie.precut.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.home.GlassDialog
import com.savie.precut.ui.theme.TextSecondary

/** Confirms cancelling a Paystack subscription (Phase 31): Pro lasts until the paid period ends. */
@Composable
internal fun CancelProDialog(onCancel: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(title = stringResource(R.string.pro_cancel_title), onDismiss = onDismiss) {
        Text(stringResource(R.string.pro_cancel_body), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        FilledButton(stringResource(R.string.pro_cancel_confirm), onCancel, Modifier.fillMaxWidth(), large = true, destructive = true)
    }
}
