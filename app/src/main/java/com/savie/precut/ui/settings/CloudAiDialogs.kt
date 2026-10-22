package com.savie.precut.ui.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.ErrorRed

/** Confirms "Delete my cloud data": what the server forgets. */
@Composable
internal fun DeleteCloudDataDialog(onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_delete_title)) },
        text = { Text(stringResource(R.string.cloud_delete_body)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = ErrorRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "What is sent": the privacy summary for cloud AI, in the app as Play's disclosure rules ask. */
@Composable
internal fun WhatIsSentDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_what_title)) },
        text = {
            Text(
                stringResource(R.string.cloud_what_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}
