package com.savie.precut.ui.editor.export

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.data.AppSettings
import com.savie.precut.model.outputSize
import com.savie.precut.model.export.ExportSettings
import com.savie.precut.model.totalDurationMs
import com.savie.precut.export.ExportKind
import com.savie.precut.ui.editor.EditorEvent
import com.savie.precut.ui.editor.EditorUiState
import com.savie.precut.ui.editor.EditorViewModel

/** Opens the export screen, asking once for notifications first (A-03) on Android 13+. */
class ExportNavigator(private val onExport: (EditorEvent.NavigateExport) -> Unit) {
    internal var pending by mutableStateOf<EditorEvent.NavigateExport?>(null)
    internal var shouldAsk: () -> Boolean = { false }

    fun navigate(event: EditorEvent.NavigateExport) {
        if (shouldAsk()) pending = event else onExport(event)
    }

    internal fun proceed() {
        pending?.let(onExport)
        pending = null
    }
}

@Composable
fun rememberExportNavigator(onExport: (EditorEvent.NavigateExport) -> Unit): ExportNavigator = remember { ExportNavigator(onExport) }

/** The export sheet (EX-01) and the notification prompt (A-03). */
@Composable
fun EditorExportHost(state: EditorUiState.Ready, viewModel: EditorViewModel, navigator: ExportNavigator, settings: AppSettings) {
    val context = LocalContext.current
    navigator.shouldAsk = { needsNotificationPrompt(context) && !settings.values.value.notificationsAsked }
    val export = viewModel.export
    val open by export.open.collectAsStateWithLifecycle()
    if (open) {
        val current by export.settings.collectAsStateWithLifecycle()
        val capabilities by export.capabilities.collectAsStateWithLifecycle()
        val saving by export.savingFrame.collectAsStateWithLifecycle()
        ExportSettingsSheet(
            settings = current,
            capabilities = capabilities,
            outputSize = state.project.outputSize(current.resolution.shortSidePx),
            durationMs = state.project.totalDurationMs(),
            savingFrame = saving,
            actions = object : ExportSheetActions {
                override fun onSettings(settings: ExportSettings) = export.onSettings(settings)
                override fun onExportVideo() = export.onExport(ExportKind.VIDEO)
                override fun onAudioOnly() = export.onExport(ExportKind.AUDIO)
                override fun onSaveFrame() = export.onSaveFrame()
                override fun onDismiss() = export.onClose()
            },
        )
    }
    val permission = rememberLauncherForActivityResult(RequestPermission()) { navigator.proceed() }
    if (navigator.pending != null) {
        val answered = { settings.update { it.copy(notificationsAsked = true) } }
        AlertDialog(
            onDismissRequest = {
                answered()
                navigator.proceed()
            },
            title = { Text(stringResource(R.string.notif_permission_title)) },
            text = { Text(stringResource(R.string.notif_permission_text)) },
            confirmButton = {
                TextButton(onClick = {
                    answered()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else navigator.proceed()
                }) { Text(stringResource(R.string.notif_permission_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    answered()
                    navigator.proceed()
                }) { Text(stringResource(R.string.notif_permission_later)) }
            },
        )
    }
}

private fun needsNotificationPrompt(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
