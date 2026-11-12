package com.savie.precut.ui.cloud

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.ai.AiFeature
import com.savie.precut.model.ai.UploadSummary
import com.savie.precut.ui.components.AiTag
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.home.GlassDialog
import com.savie.precut.ui.theme.TextSecondary

/**
 * Asked before every upload (Phase 20): what is sent, where it goes, what is kept, and that Google
 * Play checks the app. Only [onSend] may give the upload its consent
 * ([com.savie.precut.ai.UploadConsent.givenFor]). Sound sent in parts says so; [note] adds a line
 * (e.g. that the free minutes cover only the start).
 */
@Composable
fun UploadConsentDialog(summary: UploadSummary, onSend: () -> Unit, onCancel: () -> Unit, note: String? = null) {
    val context = LocalContext.current
    val length = if (summary.durationMs >= 60_000L) formatDurationMs(summary.durationMs) else stringResource(R.string.motion_seconds, summary.durationMs / 1000f)
    val size = Formatter.formatShortFileSize(context, summary.bytes)
    // The 2026 dialog, solid (it can open over the editor's video): a lime CLOUD tag marks the
    // AI upload; Send is the one pink action, the ✕ cancels.
    GlassDialog(
        title = stringResource(if (summary.feature == AiFeature.CHECK) R.string.consent_title_check else R.string.consent_title_captions),
        onDismiss = onCancel,
    ) {
        AiTag(stringResource(R.string.rd_app_cloud_tag).uppercase(), icon = R.drawable.ic_auto_awesome)
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val what = if (summary.feature == AiFeature.CHECK) R.string.consent_what_check else R.string.consent_what_captions
            val parts = summary.partBytes.size.takeIf { it > 1 }?.let { stringResource(R.string.consent_parts, it) }
            listOfNotNull(
                stringResource(what, length, size), parts, note,
                stringResource(R.string.consent_where), stringResource(R.string.consent_kept), stringResource(R.string.consent_verify),
            ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        }
        FilledButton(stringResource(R.string.consent_send), onClick = onSend, large = true, modifier = Modifier.fillMaxWidth())
    }
}
