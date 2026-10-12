package com.savie.precut.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.PreCutTheme

@Composable
fun ExportContent(
    state: ExportUiState,
    contentPadding: PaddingValues,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (uri: String) -> Unit,
    onShare: (uri: String) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            ExportUiState.Idle, is ExportUiState.Exporting -> {
                val percent = (state as? ExportUiState.Exporting)?.percent
                Text(stringResource(R.string.export_in_progress), style = MaterialTheme.typography.titleMedium)
                if (percent == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.export_percent, percent))
                }
                Text(stringResource(R.string.export_keep_open), textAlign = TextAlign.Center)
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
            ExportUiState.Saving -> {
                Text(stringResource(R.string.export_saving), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is ExportUiState.Done -> {
                Text(stringResource(R.string.export_done), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                state.uri?.let { uri ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onOpen(uri) }) { Text(stringResource(R.string.action_open)) }
                        OutlinedButton(onClick = { onShare(uri) }) { Text(stringResource(R.string.action_share)) }
                    }
                }
                Button(onClick = onDone) { Text(stringResource(R.string.action_done)) }
            }
            is ExportUiState.Failed -> {
                Text(stringResource(state.reason.messageRes()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                state.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDone) { Text(stringResource(R.string.action_close)) }
                    if (state.reason != ExportFailure.PROJECT_NOT_FOUND) {
                        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                }
            }
        }
    }
}

private fun ExportFailure.messageRes(): Int = when (this) {
    ExportFailure.PROJECT_NOT_FOUND -> R.string.editor_not_found
    ExportFailure.PERMISSION_DENIED -> R.string.export_error_permission
    ExportFailure.EXPORT_FAILED -> R.string.export_error_encode
    ExportFailure.SAVE_FAILED -> R.string.export_error_save
}

@Preview
@Composable
private fun ExportContentPreview() {
    PreCutTheme {
        ExportContent(ExportUiState.Exporting(42), PaddingValues(), {}, {}, {}, {}, {})
    }
}
