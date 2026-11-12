package com.savie.precut.ui.settings

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.ui.cloud.DAY_FLAGS
import com.savie.precut.ui.cloud.UploadConsentDialog
import com.savie.precut.ui.cloud.aiFailureMessage
import com.savie.precut.ui.components.PreCutSwitch

/**
 * Settings → Cloud AI (Phase 20): the switch, this phone's free minutes, a check that sends a test
 * phrase (after the consent dialog), deleting what the server keeps, and what is sent.
 */
@Composable
internal fun CloudAiSection(viewModel: CloudAiViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf(false) }
    // RD-H09's AI group: a lime label and hairline, lime icons, the lime switch.
    SettingsGroup(stringResource(R.string.cloud_header), ai = true) {
        SettingRow(
            R.drawable.ic_auto_awesome, stringResource(R.string.cloud_switch), stringResource(R.string.cloud_switch_note),
            trailing = { PreCutSwitch(checked = state.enabled, onCheckedChange = viewModel::onEnabled, ai = true) },
            ai = true,
        )
        SettingRow(R.drawable.ic_schedule, stringResource(R.string.cloud_minutes), minutesText(state.minutes), ai = true)
        SettingRow(R.drawable.ic_check_circle, stringResource(R.string.cloud_check), checkText(state.check), onClick = viewModel::onCheck, ai = true)
        SettingRow(R.drawable.ic_delete, stringResource(R.string.cloud_delete), deletionText(state.deletion), onClick = { confirmDelete = true })
        SettingRow(R.drawable.ic_info, stringResource(R.string.cloud_what), null, chevron = true, onClick = { explain = true })
    }

    state.consent?.let { UploadConsentDialog(it, onSend = viewModel::onConsentSend, onCancel = viewModel::onConsentCancel) }
    if (confirmDelete) DeleteCloudDataDialog(onDelete = { confirmDelete = false; viewModel.onDelete() }, onDismiss = { confirmDelete = false })
    if (explain) WhatIsSentDialog(onDismiss = { explain = false })
}

@Composable
private fun minutesText(minutes: CloudMinutes): String = when (minutes) {
    CloudMinutes.Checking -> stringResource(R.string.cloud_minutes_checking)
    CloudMinutes.NotUsed -> stringResource(R.string.cloud_minutes_not_used)
    is CloudMinutes.Known -> {
        val status = minutes.status
        val day = status.resetsAtMs?.let { DateUtils.formatDateTime(LocalContext.current, it, DAY_FLAGS) }.orEmpty()
        if (status.cloudEnabled) {
            stringResource(R.string.cloud_minutes_left, status.secondsLeft / 60, status.secondsPerMonth / 60, day)
        } else {
            stringResource(R.string.cloud_minutes_busy)
        }
    }
    is CloudMinutes.Unknown -> when (minutes.failure) {
        AiFailure.Offline -> stringResource(R.string.cloud_minutes_offline)
        AiFailure.CloudOff -> stringResource(R.string.cloud_minutes_off)
        AiFailure.NotSetUp -> stringResource(R.string.ai_failure_not_set_up)
        else -> stringResource(R.string.cloud_minutes_unknown)
    }
}

@Composable
private fun checkText(check: CloudCheck): String = when (check) {
    CloudCheck.Idle -> stringResource(R.string.cloud_check_note)
    CloudCheck.Preparing -> stringResource(R.string.cloud_check_preparing)
    CloudCheck.Sending -> stringResource(R.string.cloud_check_sending)
    is CloudCheck.Heard -> check.transcript.words.takeIf { it.isNotEmpty() }
        ?.let { words -> stringResource(R.string.cloud_check_heard, words.joinToString(" ") { it.text }) }
        ?: stringResource(R.string.cloud_check_heard_nothing)
    is CloudCheck.Failed -> aiFailureMessage(check.failure)
    CloudCheck.Cancelled -> stringResource(R.string.cloud_check_cancelled)
    CloudCheck.NoTestSound -> stringResource(R.string.cloud_check_no_sound)
}

@Composable
private fun deletionText(deletion: CloudDeletion): String = when (deletion) {
    CloudDeletion.Idle -> stringResource(R.string.cloud_delete_note)
    CloudDeletion.Deleting -> stringResource(R.string.cloud_delete_deleting)
    CloudDeletion.Done -> stringResource(R.string.cloud_delete_done)
    is CloudDeletion.Failed -> aiFailureMessage(deletion.failure)
}
