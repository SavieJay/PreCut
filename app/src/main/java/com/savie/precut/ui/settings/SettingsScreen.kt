package com.savie.precut.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.editor.export.ExportSheetActions
import com.savie.precut.ui.editor.export.ExportSettingsSheet
import com.savie.precut.ui.editor.tools.label
import com.savie.precut.ui.export.label
import com.savie.precut.model.OutputSize
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import java.util.Locale

private enum class SettingsDialog { PHOTO, RATIO, EXPORT, LICENCES }

/** H-09: new-project defaults, default export settings, the preview cache and About. */
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val values by viewModel.values.collectAsStateWithLifecycle()
    val storage by viewModel.storageInfo.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    Column(Modifier.fillMaxSize().background(Ink0).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back), tint = TextPrimary) }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Header(R.string.settings_new_projects)
            SettingRow(R.drawable.ic_schedule, stringResource(R.string.settings_photo_duration), seconds(values.photoDurationMs)) { dialog = SettingsDialog.PHOTO }
            SettingRow(R.drawable.ic_aspect_ratio, stringResource(R.string.settings_aspect_ratio), values.aspectRatio.label()) { dialog = SettingsDialog.RATIO }
            Header(R.string.settings_export)
            val export = values.export
            SettingRow(
                R.drawable.ic_movie, stringResource(R.string.settings_default_export),
                stringResource(R.string.settings_export_summary, stringResource(export.resolution.label()), export.frameRate, stringResource(export.bitrate.label()), stringResource(export.codec.label())),
            ) { dialog = SettingsDialog.EXPORT }
            Header(R.string.settings_storage)
            SettingRow(
                R.drawable.ic_cached, stringResource(R.string.settings_cache),
                storage.cacheBytes?.let { stringResource(R.string.settings_cache_size, Formatter.formatShortFileSize(context, it)) } ?: stringResource(R.string.settings_cache_measuring),
                trailing = { AccentTextButton(stringResource(R.string.settings_clear), viewModel::onClearCache, enabled = !storage.clearing && (storage.cacheBytes ?: 0L) > 0L) },
            )
            SettingRow(R.drawable.ic_palette, stringResource(R.string.settings_luts), pluralStringResource(R.plurals.settings_luts_count, storage.lutCount, storage.lutCount))
            Header(R.string.settings_about)
            SettingRow(R.drawable.ic_info, stringResource(R.string.settings_about_app), stringResource(R.string.settings_version, viewModel.version))
            SettingRow(R.drawable.ic_description, stringResource(R.string.settings_licences), null, chevron = true) { dialog = SettingsDialog.LICENCES }
        }
    }
    when (dialog) {
        SettingsDialog.PHOTO -> ChoiceDialog(
            stringResource(R.string.settings_photo_duration), PHOTO_DURATIONS, values.photoDurationMs, { seconds(it) },
            onPick = { viewModel.onPhotoDuration(it); dialog = null }, onDismiss = { dialog = null },
        )
        SettingsDialog.RATIO -> ChoiceDialog(
            stringResource(R.string.settings_aspect_ratio), com.savie.precut.model.AspectRatio.entries, values.aspectRatio, { it.label() },
            onPick = { viewModel.onAspectRatio(it); dialog = null }, onDismiss = { dialog = null },
        )
        SettingsDialog.EXPORT -> ExportSettingsSheet(
            settings = values.export, capabilities = capabilities, outputSize = OutputSize(1080, 1920), durationMs = 0L, savingFrame = false,
            defaultsOnly = true,
            actions = object : ExportSheetActions {
                override fun onSettings(settings: ExportSettings) = viewModel.onExportSettings(settings)
                override fun onExportVideo() = Unit
                override fun onAudioOnly() = Unit
                override fun onSaveFrame() = Unit
                override fun onDismiss() { dialog = null }
            },
        )
        SettingsDialog.LICENCES -> InfoDialog(stringResource(R.string.settings_licences), stringResource(R.string.settings_licences_text)) { dialog = null }
        null -> Unit
    }
}

@Composable
private fun Header(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun SettingRow(
    icon: Int,
    title: String,
    value: String?,
    chevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = TextSecondary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        }
        trailing?.invoke()
        if (chevron) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun seconds(ms: Long): String = stringResource(R.string.settings_seconds, String.format(Locale.ROOT, "%.1f", ms / 1000f))

private val PHOTO_DURATIONS = listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L, 8_000L, 10_000L)
