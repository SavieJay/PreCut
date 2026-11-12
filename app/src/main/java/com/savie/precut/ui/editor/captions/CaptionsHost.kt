package com.savie.precut.ui.editor.captions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.model.text.CaptionStyle
import com.savie.precut.ui.cloud.UploadConsentDialog
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.editor.CaptionFailure
import com.savie.precut.ui.editor.CaptionsUi
import com.savie.precut.ui.editor.EditorCaptions
import com.savie.precut.ui.theme.GlassSolid
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** Captions' sheet and dialogs, each shown for its step of [captions]. */
@Composable
fun CaptionsHost(captions: EditorCaptions) {
    val state by captions.state.collectAsStateWithLifecycle()
    val sheetActions = remember(captions) {
        object : CaptionsSheetActions {
            override fun onLanguage(language: String?) = captions.onLanguage(language)
            override fun onStyle(style: CaptionStyle) = captions.onStyle(style)
            override fun onGenerate() = captions.onGenerate()
            override fun onDismiss() = captions.onDismiss()
        }
    }
    when (val current = state) {
        CaptionsUi.Closed -> Unit
        is CaptionsUi.Options -> CaptionsSheet(current, sheetActions)
        is CaptionsUi.Preparing, is CaptionsUi.Sending -> CaptionsProgressDialog(current, captions::onStop)
        is CaptionsUi.Consent -> UploadConsentDialog(
            summary = current.summary,
            onSend = captions::onConsentSend,
            onCancel = captions::onConsentCancel,
            note = if (current.coversAll) null else stringResource(R.string.consent_covers_part, formatDurationMs(current.summary.durationMs)),
        )
        is CaptionsUi.Failed -> ResultDialog(stringResource(R.string.captions_failed_title), listOf(captionFailureMessage(current.failure)), captions::onDismiss)
        is CaptionsUi.Partial -> {
            val covered = stringResource(R.string.captions_partial_body, formatDurationMs(current.coveredMs), formatDurationMs(current.durationMs))
            val why = current.failure?.let { captionFailureMessage(CaptionFailure.Cloud(it)) } ?: stringResource(R.string.captions_partial_stopped)
            ResultDialog(stringResource(R.string.captions_partial_title), listOf(covered, why), captions::onDismiss)
        }
    }
}

@Composable
private fun ResultDialog(title: String, lines: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassSolid,
        shape = Radius.sheet,
        title = { Text(title, style = PreCutType.panelTitle, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
            }
        },
        confirmButton = { FilledButton(stringResource(R.string.action_done), onDismiss) },
    )
}
