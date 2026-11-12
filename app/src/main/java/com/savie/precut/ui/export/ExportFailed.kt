package com.savie.precut.ui.export

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.export.ExportFailure
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutTopBar
import com.savie.precut.ui.theme.ErrorRed
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glassRow

// EX-05 failure screens (RD-EX05 encoder, RD-EX05b storage): an ErrorRed icon tile, the reason, then retry and the way out.

@Composable
internal fun ColumnScope.Failed(state: ExportUiState.Failed, recovery: ExportRecovery?, actions: ExportActions) {
    val context = LocalContext.current
    val storage = state.reason == ExportFailure.LOW_STORAGE
    val canLower = state.reason == ExportFailure.ENCODER && recovery != null && recovery.lower != recovery.resolution
    PreCutTopBar(stringResource(R.string.export_title))
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(ErrorTileShape)
                .background(Brush.radialGradient(listOf(ErrorRed.copy(alpha = 0.32f), ErrorRed.copy(alpha = 0.06f))))
                .border(1.dp, ErrorRed.copy(alpha = 0.5f), ErrorTileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(if (storage) R.drawable.ic_sd_card_alert else R.drawable.ic_error), contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(state.reason.messageRes()), style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
            val explanation = when {
                canLower ->
                    stringResource(R.string.export_error_encoder, stringResource(recovery.resolution.label()), stringResource(recovery.lower.label()))
                storage -> stringResource(
                    R.string.export_storage_text,
                    Formatter.formatShortFileSize(context, state.neededBytes), Formatter.formatShortFileSize(context, state.freeBytes),
                ) + (recovery?.cacheBytes?.takeIf { it > 0 }?.let { " " + stringResource(R.string.export_storage_cache, Formatter.formatShortFileSize(context, it)) } ?: "")
                else -> state.detail
            }
            explanation?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val hasCache = (recovery?.cacheBytes ?: 0L) > 0L
        when {
            canLower -> {
                RetryButton(stringResource(R.string.export_retry_res, stringResource(recovery.resolution.label())), actions::onRetry)
                FilledButton(stringResource(R.string.export_try_res, stringResource(recovery.lower.label())), actions::onTryLower, Modifier.weight(1f), large = true)
            }
            storage && hasCache -> {
                RetryButton(stringResource(R.string.action_retry), actions::onRetry)
                FilledButton(stringResource(R.string.export_clear_cache), actions::onClearCache, Modifier.weight(1f), large = true)
            }
            state.reason != ExportFailure.PROJECT_NOT_FOUND ->
                FilledButton(stringResource(R.string.action_retry), actions::onRetry, Modifier.weight(1f), icon = R.drawable.ic_refresh, large = true)
            else -> FilledButton(stringResource(R.string.export_back_to_edit), actions::onBackToEdit, Modifier.weight(1f), large = true)
        }
    }
    // The quiet way out under the pill (the code's Back to edit; RD-EX05 draws it as the top bar's arrow).
    if (state.reason != ExportFailure.PROJECT_NOT_FOUND) {
        AccentTextButton(
            stringResource(R.string.export_back_to_edit),
            actions::onBackToEdit,
            Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
            color = TextSecondary,
        )
    } else {
        Spacer(Modifier.height(16.dp))
    }
}

/** RD-EX05's 56 dp round glass retry beside the main pill. */
@Composable
private fun RetryButton(description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).glassRow(CircleShape).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_refresh), contentDescription = description, tint = TextPrimary)
    }
}

private val ErrorTileShape = RoundedCornerShape(40.dp)

private fun ExportFailure.messageRes(): Int = when (this) {
    ExportFailure.PROJECT_NOT_FOUND -> R.string.editor_not_found
    ExportFailure.PERMISSION_DENIED -> R.string.export_error_permission
    ExportFailure.EXPORT_FAILED -> R.string.export_error_encode
    ExportFailure.SAVE_FAILED -> R.string.export_error_save
    ExportFailure.ENCODER -> R.string.export_failed_title
    ExportFailure.LOW_STORAGE -> R.string.export_storage_title
}
