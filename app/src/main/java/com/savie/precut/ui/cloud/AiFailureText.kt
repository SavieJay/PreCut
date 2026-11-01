package com.savie.precut.ui.cloud

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.ai.AiFailure

/** What to tell the user about a cloud AI failure. */
@Composable
fun aiFailureMessage(failure: AiFailure): String {
    val context = LocalContext.current
    fun day(ms: Long?): String? = ms?.let { DateUtils.formatDateTime(context, it, DAY_FLAGS) }
    return when (failure) {
        AiFailure.CloudOff -> stringResource(R.string.ai_failure_cloud_off)
        AiFailure.NotSetUp -> stringResource(R.string.ai_failure_not_set_up)
        AiFailure.Offline -> stringResource(R.string.ai_failure_offline)
        AiFailure.NoPlayServices -> stringResource(R.string.ai_failure_no_play_services)
        AiFailure.AppNotVerified -> stringResource(R.string.ai_failure_app_not_verified)
        AiFailure.DeviceNotVerified -> stringResource(R.string.ai_failure_device_not_verified)
        is AiFailure.QuotaUsed -> day(failure.resetsAtMs)?.let { stringResource(R.string.ai_failure_quota_used, it) }
            ?: stringResource(R.string.ai_failure_quota_used_soon)
        is AiFailure.BudgetReached -> day(failure.resetsAtMs)?.let { stringResource(R.string.ai_failure_budget_reached, it) }
            ?: stringResource(R.string.ai_failure_budget_reached_soon)
        AiFailure.Paused -> stringResource(R.string.ai_failure_paused)
        is AiFailure.TooLong -> (failure.maxSeconds / 60).let { pluralStringResource(R.plurals.ai_failure_too_long, it, it) }
        AiFailure.TooLarge -> stringResource(R.string.ai_failure_too_large)
        AiFailure.BadAudio -> stringResource(R.string.ai_failure_bad_audio)
        AiFailure.EditNotReady -> stringResource(R.string.ai_failure_edit_not_ready)
        is AiFailure.EditRunsUsed -> day(failure.resetsAtMs)?.let { stringResource(R.string.ai_failure_edit_runs_used, it) }
            ?: stringResource(R.string.ai_failure_edit_runs_used_soon)
        AiFailure.StepsUsed -> stringResource(R.string.ai_failure_steps_used)
        AiFailure.BadPurchase -> stringResource(R.string.ai_failure_bad_purchase)
        AiFailure.UpdateApp -> stringResource(R.string.ai_failure_update_app)
        AiFailure.Busy -> stringResource(R.string.ai_failure_busy)
    }
}

/** "1 Nov": when free minutes or the budget come back. */
internal const val DAY_FLAGS = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR
