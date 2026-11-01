package com.savie.precut.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.savie.precut.R

/** About: the privacy policy, terms and support on PreCut's website, opened in the browser. */
@Composable
internal fun WebsiteRows(pages: WebsitePages) {
    val context = LocalContext.current
    SettingRow(R.drawable.ic_description, stringResource(R.string.settings_privacy_policy), null, chevron = true) { open(context, pages.privacy) }
    SettingRow(R.drawable.ic_description, stringResource(R.string.settings_terms), null, chevron = true) { open(context, pages.terms) }
    SettingRow(R.drawable.ic_info, stringResource(R.string.settings_support), stringResource(R.string.settings_support_summary), chevron = true) {
        open(context, pages.support)
    }
}

private fun open(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.settings_no_browser, url), Toast.LENGTH_LONG).show()
    }
}
