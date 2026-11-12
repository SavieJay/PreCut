package com.savie.precut.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.model.pro.PlanNotice
import com.savie.precut.model.pro.ProCatalog
import com.savie.precut.model.pro.planNotice
import com.savie.precut.ui.cloud.DAY_FLAGS
import com.savie.precut.ui.cloud.aiFailureMessage
import com.savie.precut.ui.theme.Accent

/**
 * Settings → PreCut Pro (Phase 25, B3): the plan and when it renews or ends, payment notices,
 * Manage in Google Play, Restore purchases, and this month's cloud use; Get Pro when free.
 */
@Composable
internal fun ProSection(onGetPro: () -> Unit, viewModel: ProSectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onShown() }
    SettingsGroup(stringResource(R.string.pro_settings_header)) { ProRows(state, onGetPro, viewModel::onRestore) }
}

@Composable
private fun ProRows(state: ProSectionState, onGetPro: () -> Unit, onRestore: () -> Unit) {
    val context = LocalContext.current
    val plan = state.known.plan
    val day = plan?.expiresAtMs?.let { DateUtils.formatDateTime(context, it, DAY_FLAGS or DateUtils.FORMAT_SHOW_YEAR) }
    if (state.isPro) {
        val name = stringResource(if (plan?.basePlanId == ProCatalog.MONTHLY) R.string.pro_plan_monthly else R.string.pro_plan_yearly)
        val detail = when {
            day == null -> null
            plan.trial == true -> stringResource(R.string.pro_trial_ends, day)
            plan.autoRenewing == true -> stringResource(R.string.pro_renews, day)
            else -> stringResource(R.string.pro_ends, day)
        }
        SettingRow(R.drawable.ic_diamond, name, detail)
    } else {
        SettingRow(R.drawable.ic_diamond, stringResource(R.string.pro_get), stringResource(R.string.pro_get_summary), chevron = true, onClick = onGetPro)
    }
    planNotice(plan)?.let { notice ->
        Text(stringResource(noticeText(notice)), style = MaterialTheme.typography.bodySmall, color = Accent, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    state.known.status?.let { status ->
        SettingRow(
            R.drawable.ic_closed_caption, stringResource(R.string.pro_usage_minutes),
            (status.secondsLeft / 60).let { pluralStringResource(R.plurals.pro_usage_minutes_left, it, it, status.secondsPerMonth / 60) },
        )
        SettingRow(
            R.drawable.ic_auto_awesome, stringResource(R.string.pro_usage_ai_edits),
            pluralStringResource(R.plurals.pro_usage_runs, status.editRunsLeft, status.editRunsLeft, status.editRunsPerMonth),
        )
    }
    if (plan?.state != null) {
        SettingRow(R.drawable.ic_open_in_new, stringResource(R.string.pro_manage), null, chevron = true) {
            val url = "https://play.google.com/store/account/subscriptions?sku=${ProCatalog.PRODUCT_ID}&package=${context.packageName}"
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (_: ActivityNotFoundException) {
                // Nothing on the phone can open it.
            }
        }
    }
    SettingRow(
        R.drawable.ic_refresh, stringResource(R.string.pro_restore),
        when {
            state.restoring -> stringResource(R.string.pro_restoring)
            state.failure != null -> aiFailureMessage(requireNotNull(state.failure))
            state.restored -> stringResource(if (state.isPro) R.string.pro_restored else R.string.pro_nothing_to_restore)
            else -> null
        },
        onClick = onRestore,
    )
}

private fun noticeText(notice: PlanNotice): Int = when (notice) {
    PlanNotice.PAYMENT_ISSUE -> R.string.pro_notice_payment
    PlanNotice.ON_HOLD -> R.string.pro_notice_on_hold
    PlanNotice.PAUSED -> R.string.pro_notice_paused
    PlanNotice.ENDING -> R.string.pro_notice_ending
}
