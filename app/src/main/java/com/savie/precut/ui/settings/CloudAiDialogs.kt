package com.savie.precut.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.home.GlassDialog
import com.savie.precut.ui.theme.TextSecondary

/** Confirms "Delete my cloud data": what the server forgets, and an ErrorRed Delete pill. */
@Composable
internal fun DeleteCloudDataDialog(onDelete: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(title = stringResource(R.string.cloud_delete_title), onDismiss = onDismiss) {
        Text(stringResource(R.string.cloud_delete_body), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        FilledButton(
            text = stringResource(R.string.action_delete),
            onClick = onDelete,
            icon = R.drawable.ic_delete,
            large = true,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "What is sent": the privacy summary for cloud AI, in the app as Play's disclosure rules ask. */
@Composable
internal fun WhatIsSentDialog(onDismiss: () -> Unit) {
    GlassDialog(title = stringResource(R.string.cloud_what_title), onDismiss = onDismiss) {
        Text(
            stringResource(R.string.cloud_what_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        )
        FilledButton(text = stringResource(R.string.action_done), onClick = onDismiss, large = true, modifier = Modifier.fillMaxWidth())
    }
}
