package com.savie.precut.ui.cloud

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.ai.AiFeature
import com.savie.precut.model.ai.UploadSummary

/**
 * Asked before every upload (Phase 20): what is sent, where it goes, what is kept, and that Google
 * Play checks the app. Only [onSend] may give the upload its consent
 * ([com.savie.precut.ai.UploadConsent.givenFor]).
 */
@Composable
fun UploadConsentDialog(summary: UploadSummary, onSend: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val length = stringResource(R.string.motion_seconds, summary.durationMs / 1000f)
    val size = Formatter.formatShortFileSize(context, summary.bytes)
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(stringResource(if (summary.feature == AiFeature.CHECK) R.string.consent_title_check else R.string.consent_title_captions))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val what = if (summary.feature == AiFeature.CHECK) R.string.consent_what_check else R.string.consent_what_captions
                listOf(stringResource(what, length, size), stringResource(R.string.consent_where), stringResource(R.string.consent_kept), stringResource(R.string.consent_verify))
                    .forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onSend) { Text(stringResource(R.string.consent_send)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}
