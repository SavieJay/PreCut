package com.savie.precut.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutSwitch
import com.savie.precut.ui.home.GlassDialog
import com.savie.precut.ui.theme.TextSecondary

private enum class AccountDialog { SIGN_OUT, DELETE }

/** Settings → Account (Phase 30); nothing while no one is signed in (a build without accounts). */
@Composable
internal fun AccountSection(viewModel: AccountViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    if (!ui.shown) return
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }
    SettingsGroup(stringResource(R.string.account_title)) {
        SettingRow(
            R.drawable.ic_account_circle,
            ui.email?.let { stringResource(R.string.account_signed_in_as, it) } ?: stringResource(R.string.account_signed_in),
            null,
        )
        SettingRow(
            R.drawable.ic_cloud,
            stringResource(R.string.account_storage),
            ui.cloud?.let {
                stringResource(R.string.account_storage_used, Formatter.formatShortFileSize(context, it.storageUsed), Formatter.formatShortFileSize(context, it.storageLimit))
            } ?: stringResource(R.string.account_storage_unknown),
        )
        SettingRow(
            R.drawable.ic_cloud, stringResource(R.string.account_mobile_data), stringResource(R.string.account_mobile_data_body),
            trailing = { PreCutSwitch(ui.mobileData, viewModel::onMobileData) },
        ) { viewModel.onMobileData(!ui.mobileData) }
        SettingRow(R.drawable.ic_logout, stringResource(R.string.account_sign_out), null, onClick = { dialog = AccountDialog.SIGN_OUT })
        SettingRow(R.drawable.ic_delete, stringResource(R.string.account_delete), null, onClick = { dialog = AccountDialog.DELETE })
    }
    when (dialog) {
        AccountDialog.SIGN_OUT -> GlassDialog(title = stringResource(R.string.account_sign_out_title), onDismiss = { dialog = null }) {
            Text(stringResource(R.string.account_sign_out_body), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            FilledButton(stringResource(R.string.account_sign_out), { dialog = null; viewModel.onSignOut() }, Modifier.fillMaxWidth(), icon = R.drawable.ic_logout, large = true)
        }
        AccountDialog.DELETE -> DeleteAccountDialog(
            deleting = ui.deleting,
            failed = ui.deleteFailed,
            onDelete = viewModel::onDelete,
            onDismiss = { dialog = null; viewModel.onDeleteFailedSeen() },
        )
        null -> Unit
    }
}
