package com.savie.precut.ui.settings

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

/** About → Crash reports: how many are kept, with Share (the newest, through the share sheet) and Delete. */
@Composable
internal fun CrashReportsRow(info: CrashInfo, newest: suspend () -> File?, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.crash_share_title)
    val subject = stringResource(R.string.crash_share_subject)
    val value = info.newestMs?.let { ms ->
        pluralStringResource(R.plurals.settings_crash_saved, info.count, info.count, DateUtils.formatDateTime(context, ms, DATE_FLAGS))
    } ?: stringResource(R.string.settings_crash_none)
    SettingRow(
        R.drawable.ic_bug_report, stringResource(R.string.settings_crash_reports), value,
        trailing = if (info.count == 0) null else {
            {
                Row {
                    AccentTextButton(stringResource(R.string.settings_crash_delete), onDelete, color = TextSecondary)
                    AccentTextButton(stringResource(R.string.settings_crash_share), onClick = {
                        scope.launch { newest()?.let { share(context, it, subject, shareTitle) } }
                    })
                }
            }
        },
    )
}

private fun share(context: Context, report: File, subject: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", report)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, title))
}

private const val DATE_FLAGS = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
